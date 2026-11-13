package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class DrinkType(
  val key: String,
  val label: String,
  val defaultAmountMl: Long,
) {
  WATER("water", "Pure Water", 250L),
  SPARKLING("sparkling", "Sparkling", 330L),
  TEA("tea", "Herbal Tea", 240L),
  ELECTROLYTES("electrolytes", "Electrolytes", 500L),
  COFFEE("coffee", "Coffee", 180L);

  companion object {
    fun fromKey(key: String): DrinkType =
      entries.firstOrNull { it.key == key } ?: WATER
  }
}

enum class PreferredUnit(val key: String, val label: String) {
  ML("ml", "Milliliters (ml)"),
  OZ("oz", "Fluid Ounces (oz)");

  companion object {
    fun fromKey(key: String): PreferredUnit =
      entries.firstOrNull { it.key == key } ?: ML
  }
}

@IgnoreExtraProperties
data class UserProfile(
  val userId: String = "",
  val dailyGoalMl: Long = 2500L,
  val preferredUnit: String = PreferredUnit.ML.key,
  val notificationsEnabled: Boolean = true,
  val reminderIntervalMinutes: Long = 120L,
  val wakeHour: Long = 8L,
  val sleepHour: Long = 22L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class WaterLog(
  @DocumentId val id: String = "",
  val userId: String = "",
  val amountMl: Long = 250L,
  val drinkType: String = DrinkType.WATER.key,
  val dateKey: String = "",
  val loggedAtMillis: Long = 0L,
  val note: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

object HydrationDateUtils {
  private const val DATE_PATTERN = "yyyy-MM-dd"
  private const val TIME_PATTERN = "h:mm a"

  fun dateKeyForMillis(millis: Long): String {
    val formatter = SimpleDateFormat(DATE_PATTERN, Locale.US)
    return formatter.format(Date(millis))
  }

  fun todayDateKey(): String = dateKeyForMillis(System.currentTimeMillis())

  fun lastNDaysDateKeys(days: Int): List<String> {
    val formatter = SimpleDateFormat(DATE_PATTERN, Locale.US)
    val cal = Calendar.getInstance()
    val keys = ArrayList<String>(days)
    cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
    repeat(days) {
      keys.add(formatter.format(cal.time))
      cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return keys
  }

  fun shortDayLabel(dateKey: String): String {
    return try {
      val parser = SimpleDateFormat(DATE_PATTERN, Locale.US)
      val date = parser.parse(dateKey) ?: return dateKey.takeLast(2)
      SimpleDateFormat("EEE", Locale.US).format(date)
    } catch (_: Exception) {
      dateKey.takeLast(2)
    }
  }

  fun formatTime(millis: Long): String {
    val formatter = SimpleDateFormat(TIME_PATTERN, Locale.US)
    return formatter.format(Date(millis))
  }
}

object HydrationFormatters {
  private const val ML_PER_FL_OZ = 29.5735

  fun formatVolume(amountMl: Long, unit: PreferredUnit): String {
    return when (unit) {
      PreferredUnit.ML -> "${amountMl} ml"
      PreferredUnit.OZ -> {
        val oz = (amountMl / ML_PER_FL_OZ).roundToInt()
        "$oz oz"
      }
    }
  }

  fun formatGoalVolume(amountMl: Long, unit: PreferredUnit): String {
    return when (unit) {
      PreferredUnit.ML -> {
        if (amountMl >= 1000 && amountMl % 100 == 0L) {
          val liters = amountMl / 1000.0
          String.format(Locale.US, "%.1f L (%d ml)", liters, amountMl)
        } else {
          "$amountMl ml"
        }
      }
      PreferredUnit.OZ -> {
        val oz = (amountMl / ML_PER_FL_OZ).roundToInt()
        "$oz fl oz"
      }
    }
  }

  fun formatHour(hour: Int): String {
    val normalized = ((hour % 24) + 24) % 24
    val suffix = if (normalized < 12) "AM" else "PM"
    val hour12 = when {
      normalized == 0 -> 12
      normalized > 12 -> normalized - 12
      else -> normalized
    }
    return "$hour12:00 $suffix"
  }

  fun formatInterval(minutes: Long): String {
    return when {
      minutes < 60 -> "$minutes min"
      minutes % 60L == 0L -> {
        val hours = minutes / 60L
        if (hours == 1L) "Every 1 hour" else "Every $hours hours"
      }
      else -> {
        val hours = minutes / 60L
        val rem = minutes % 60L
        "Every ${hours}h ${rem}m"
      }
    }
  }
}
