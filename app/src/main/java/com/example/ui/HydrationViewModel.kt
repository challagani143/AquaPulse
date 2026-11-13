package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DrinkType
import com.example.data.HydrationDateUtils
import com.example.data.HydrationRepository
import com.example.data.PreferredUnit
import com.example.data.UserProfile
import com.example.data.WaterLog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayHydrationSummary(
  val dateKey: String,
  val dayLabel: String,
  val totalMl: Long,
  val goalMl: Long,
) {
  val goalMet: Boolean
    get() = totalMl >= goalMl
}

data class HydrationDashboardData(
  val profile: UserProfile,
  val todayLogs: List<WaterLog>,
  val allLogs: List<WaterLog>,
  val todayTotalMl: Long,
  val weeklySummaries: List<DayHydrationSummary>,
  val currentStreakDays: Int,
  val averageDailyMl: Long,
) {
  val unit: PreferredUnit
    get() = PreferredUnit.fromKey(profile.preferredUnit)

  val progressFraction: Float
    get() = (todayTotalMl.toFloat() / profile.dailyGoalMl.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1.5f)

  val clampedProgress: Float
    get() = progressFraction.coerceIn(0f, 1f)

  val remainingMl: Long
    get() = (profile.dailyGoalMl - todayTotalMl).coerceAtLeast(0L)
}

sealed interface HydrationUiState {
  data object Loading : HydrationUiState
  data class Success(val data: HydrationDashboardData) : HydrationUiState
  data class Error(val message: String) : HydrationUiState
}

class HydrationViewModel(
  private val repository: HydrationRepository,
  private val currentUserId: String,
) : ViewModel() {

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  private val _isMutating = MutableStateFlow(false)
  val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

  init {
    viewModelScope.launch {
      repository.ensureUserProfile(currentUserId).onFailure { err ->
        Log.w(TAG, "Failed to initialize user profile", err)
      }
    }
  }

  val uiState: StateFlow<HydrationUiState> =
    combine(
      repository.observeUserProfile(currentUserId),
      repository.observeWaterLogs(currentUserId),
    ) { profileOrNull, logs ->
      val profile = profileOrNull ?: UserProfile(userId = currentUserId)
      val todayKey = HydrationDateUtils.todayDateKey()
      val todayLogs = logs.filter { it.dateKey == todayKey }
      val todayTotal = todayLogs.sumOf { it.amountMl }

      val totalsByDay = logs.groupBy { it.dateKey }.mapValues { (_, list) -> list.sumOf { it.amountMl } }
      val last7Keys = HydrationDateUtils.lastNDaysDateKeys(7)
      val weekly =
        last7Keys.map { key ->
          DayHydrationSummary(
            dateKey = key,
            dayLabel = HydrationDateUtils.shortDayLabel(key),
            totalMl = totalsByDay[key] ?: 0L,
            goalMl = profile.dailyGoalMl,
          )
        }

      val streak = computeStreak(totalsByDay, profile.dailyGoalMl)
      val activeDays = weekly.filter { it.totalMl > 0L }
      val avg =
        if (activeDays.isEmpty()) 0L
        else activeDays.sumOf { it.totalMl } / activeDays.size.toLong()

      HydrationUiState.Success(
        HydrationDashboardData(
          profile = profile,
          todayLogs = todayLogs,
          allLogs = logs,
          todayTotalMl = todayTotal,
          weeklySummaries = weekly,
          currentStreakDays = streak,
          averageDailyMl = avg,
        )
      ) as HydrationUiState
    }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing hydration data", rootError)
        emit(HydrationUiState.Error(rootError.message ?: "Unable to load hydration data"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HydrationUiState.Loading,
      )

  fun logDrink(amountMl: Long, drinkType: DrinkType, note: String = "") {
    if (_isMutating.value) return
    val now = System.currentTimeMillis()
    val todayKey = HydrationDateUtils.dateKeyForMillis(now)
    viewModelScope.launch {
      _isMutating.value = true
      repository
        .addWaterLog(
          userId = currentUserId,
          amountMl = amountMl,
          drinkType = drinkType.key,
          dateKey = todayKey,
          loggedAtMillis = now,
          note = note,
        )
        .onSuccess {
          _statusMessage.value = "Added +$amountMl ml ${drinkType.label}"
        }
        .onFailure { err ->
          _statusMessage.value = err.localizedMessage ?: "Couldn't save drink"
        }
      _isMutating.value = false
    }
  }

  fun deleteLog(logId: String) {
    if (logId.isBlank()) return
    viewModelScope.launch {
      repository
        .deleteWaterLog(currentUserId, logId)
        .onSuccess {
          _statusMessage.value = "Entry removed"
        }
        .onFailure { err ->
          _statusMessage.value = err.localizedMessage ?: "Couldn't remove entry"
        }
    }
  }

  fun updateProfile(
    dailyGoalMl: Long,
    preferredUnit: PreferredUnit,
    notificationsEnabled: Boolean,
    reminderIntervalMinutes: Long,
    wakeHour: Int,
    sleepHour: Int,
    feedbackMessage: String? = null,
  ) {
    viewModelScope.launch {
      repository
        .updateUserProfile(
          userId = currentUserId,
          dailyGoalMl = dailyGoalMl,
          preferredUnit = preferredUnit.key,
          notificationsEnabled = notificationsEnabled,
          reminderIntervalMinutes = reminderIntervalMinutes,
          wakeHour = wakeHour.toLong(),
          sleepHour = sleepHour.toLong(),
        )
        .onSuccess {
          if (feedbackMessage != null) {
            _statusMessage.value = feedbackMessage
          }
        }
        .onFailure { err ->
          _statusMessage.value = err.localizedMessage ?: "Failed to update settings"
        }
    }
  }

  fun clearStatusMessage() {
    _statusMessage.value = null
  }

  private fun computeStreak(totalsByDay: Map<String, Long>, goalMl: Long): Int {
    val keys = HydrationDateUtils.lastNDaysDateKeys(30)
    if (keys.isEmpty()) return 0
    var streak = 0
    val todayKey = keys.last()
    val todayMet = (totalsByDay[todayKey] ?: 0L) >= goalMl
    val startIdx = if (todayMet) keys.lastIndex else keys.lastIndex - 1
    for (i in startIdx downTo 0) {
      val total = totalsByDay[keys[i]] ?: 0L
      if (total >= goalMl) {
        streak++
      } else {
        break
      }
    }
    return streak
  }

  private companion object {
    const val TAG = "HydrationVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
