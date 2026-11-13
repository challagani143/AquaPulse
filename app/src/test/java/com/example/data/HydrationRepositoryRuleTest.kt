package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureAndUpdateUserProfile_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = HydrationRepository(firestore, auth)

    val ensureResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile(uid) }
    assertTrue("ensureUserProfile failed: ${ensureResult.exceptionOrNull()}", ensureResult.isSuccess)

    val updateResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.updateUserProfile(
          userId = uid,
          dailyGoalMl = 3000L,
          preferredUnit = PreferredUnit.OZ.key,
          notificationsEnabled = true,
          reminderIntervalMinutes = 90L,
          wakeHour = 7L,
          sleepHour = 23L,
        )
      }
    assertTrue("updateUserProfile failed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserProfile(uid).first { it?.dailyGoalMl == 3000L }
      }
    assertNotNull(observed)
    assertEquals(PreferredUnit.OZ.key, observed?.preferredUnit)
    assertEquals(90L, observed?.reminderIntervalMinutes)
  }

  @Test
  fun addAndDeleteWaterLog_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = HydrationRepository(firestore, auth)
    val today = HydrationDateUtils.todayDateKey()

    val addResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addWaterLog(
          userId = uid,
          amountMl = 350L,
          drinkType = DrinkType.WATER.key,
          dateKey = today,
          loggedAtMillis = System.currentTimeMillis(),
          note = "Desk bottle",
        )
      }
    assertTrue("addWaterLog failed: ${addResult.exceptionOrNull()}", addResult.isSuccess)
    val logId = addResult.getOrThrow()
    assertTrue(logId.isNotBlank())

    val logs =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeWaterLogs(uid).first { list -> list.any { it.id == logId } }
      }
    val createdLog = logs.first { it.id == logId }
    assertEquals(350L, createdLog.amountMl)
    assertNotNull(createdLog.createdAt)

    val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.deleteWaterLog(uid, logId) }
    assertTrue("deleteWaterLog failed: ${deleteResult.exceptionOrNull()}", deleteResult.isSuccess)
  }

  @Test
  fun observeWaterLogs_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = HydrationRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo
        .addWaterLog(
          userId = aliceUid,
          amountMl = 250L,
          drinkType = DrinkType.WATER.key,
          dateKey = HydrationDateUtils.todayDateKey(),
          loggedAtMillis = System.currentTimeMillis(),
        )
        .getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = HydrationRepository(firestore, auth)

    var thrown: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeWaterLogs(aliceUid).first() }
    } catch (e: Throwable) {
      thrown = e
    }
    assertNotNull("Expected PERMISSION_DENIED exception for cross-user read", thrown)
    val firestoreException = checkNotNull(thrown!!.unwrapFirestoreException()) {
      "Expected nested FirebaseFirestoreException, got $thrown"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  @Test
  fun observeWaterLogs_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = HydrationRepository(firestore, auth)

    var thrown: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeWaterLogs("some_user_123").first() }
    } catch (e: Throwable) {
      thrown = e
    }
    assertNotNull("Expected PERMISSION_DENIED exception for unauthenticated read", thrown)
    val firestoreException = checkNotNull(thrown!!.unwrapFirestoreException()) {
      "Expected nested FirebaseFirestoreException, got $thrown"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_aqua@test.com"
    const val BOB_EMAIL = "bob_aqua@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
