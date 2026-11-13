package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class HydrationRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    val docRef = db.collection("users").document(userId)
    emitAll(
      docRef.snapshots().map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(
            UserProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        } else {
          null
        }
      }.catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
        }
        throw error
      }
    )
  }

  suspend fun ensureUserProfile(userId: String): Result<UserProfile> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    if (uid != userId) {
      return Result.failure(IllegalArgumentException("Authenticated user mismatch"))
    }
    val docRef = db.collection("users").document(userId)
    return try {
      val snapshot = docRef.get().await()
      if (snapshot.exists()) {
        val profile =
          snapshot.toObject(
            UserProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ) ?: UserProfile(userId = userId)
        Result.success(profile)
      } else {
        val defaults = UserProfile(userId = userId)
        val payload = mapOf(
          "userId" to userId,
          "dailyGoalMl" to defaults.dailyGoalMl,
          "preferredUnit" to defaults.preferredUnit,
          "notificationsEnabled" to defaults.notificationsEnabled,
          "reminderIntervalMinutes" to defaults.reminderIntervalMinutes,
          "wakeHour" to defaults.wakeHour,
          "sleepHour" to defaults.sleepHour,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
        Result.success(defaults)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateUserProfile(
    userId: String,
    dailyGoalMl: Long,
    preferredUnit: String,
    notificationsEnabled: Boolean,
    reminderIntervalMinutes: Long,
    wakeHour: Long,
    sleepHour: Long,
  ): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    if (uid != userId) {
      return Result.failure(IllegalArgumentException("Authenticated user mismatch"))
    }
    val docRef = db.collection("users").document(userId)
    return try {
      val existing = docRef.get().await()
      if (!existing.exists()) {
        val payload = mapOf(
          "userId" to userId,
          "dailyGoalMl" to dailyGoalMl.coerceIn(500L, 10000L),
          "preferredUnit" to preferredUnit,
          "notificationsEnabled" to notificationsEnabled,
          "reminderIntervalMinutes" to reminderIntervalMinutes.coerceIn(30L, 480L),
          "wakeHour" to wakeHour.coerceIn(0L, 23L),
          "sleepHour" to sleepHour.coerceIn(0L, 23L),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      } else {
        val updates = mapOf(
          "dailyGoalMl" to dailyGoalMl.coerceIn(500L, 10000L),
          "preferredUnit" to preferredUnit,
          "notificationsEnabled" to notificationsEnabled,
          "reminderIntervalMinutes" to reminderIntervalMinutes.coerceIn(30L, 480L),
          "wakeHour" to wakeHour.coerceIn(0L, 23L),
          "sleepHour" to sleepHour.coerceIn(0L, 23L),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.update(updates).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  fun observeWaterLogs(userId: String): Flow<List<WaterLog>> = flow {
    val colRef = db.collection("users").document(userId).collection("water_logs")
    emitAll(
      colRef.snapshots().map { snapshot ->
        snapshot
          .toObjects(
            WaterLog::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
          .sortedByDescending { it.loggedAtMillis }
      }.catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
        }
        throw error
      }
    )
  }

  suspend fun addWaterLog(
    userId: String,
    amountMl: Long,
    drinkType: String,
    dateKey: String,
    loggedAtMillis: Long,
    note: String = "",
  ): Result<String> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    if (uid != userId) {
      return Result.failure(IllegalArgumentException("Authenticated user mismatch"))
    }
    val colRef = db.collection("users").document(userId).collection("water_logs")
    val docRef = colRef.document()
    val payload = mapOf(
      "userId" to userId,
      "amountMl" to amountMl.coerceIn(10L, 5000L),
      "drinkType" to drinkType,
      "dateKey" to dateKey,
      "loggedAtMillis" to loggedAtMillis,
      "note" to note.take(80),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    return try {
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateWaterLog(userId: String, log: WaterLog): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    if (uid != userId) {
      return Result.failure(IllegalArgumentException("Authenticated user mismatch"))
    }
    require(log.id.isNotBlank()) { "WaterLog ID must not be blank for update" }
    requireNotNull(log.createdAt) { "WaterLog createdAt must not be null for update" }
    val docRef = db.collection("users").document(userId).collection("water_logs").document(log.id)
    return try {
      docRef.set(log.copy(updatedAt = null)).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteWaterLog(userId: String, logId: String): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    if (uid != userId) {
      return Result.failure(IllegalArgumentException("Authenticated user mismatch"))
    }
    val docRef = db.collection("users").document(userId).collection("water_logs").document(logId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }
}
