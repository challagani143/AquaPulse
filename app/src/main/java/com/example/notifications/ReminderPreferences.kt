package com.example.notifications

import android.content.Context

object ReminderPreferences {
  private const val PREFS_NAME = "aquapulse_reminder_prefs"
  private const val KEY_ENABLED = "enabled"
  private const val KEY_INTERVAL_MINUTES = "interval_minutes"
  private const val KEY_WAKE_HOUR = "wake_hour"
  private const val KEY_SLEEP_HOUR = "sleep_hour"
  private const val KEY_DAILY_GOAL_ML = "daily_goal_ml"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun saveSchedule(
    context: Context,
    enabled: Boolean,
    intervalMinutes: Long,
    wakeHour: Int,
    sleepHour: Int,
    dailyGoalMl: Long,
  ) {
    prefs(context)
      .edit()
      .putBoolean(KEY_ENABLED, enabled)
      .putLong(KEY_INTERVAL_MINUTES, intervalMinutes.coerceIn(30L, 480L))
      .putInt(KEY_WAKE_HOUR, wakeHour.coerceIn(0, 23))
      .putInt(KEY_SLEEP_HOUR, sleepHour.coerceIn(0, 23))
      .putLong(KEY_DAILY_GOAL_ML, dailyGoalMl.coerceIn(500L, 10000L))
      .apply()
  }

  fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

  fun getIntervalMinutes(context: Context): Long =
    prefs(context).getLong(KEY_INTERVAL_MINUTES, 120L).coerceIn(30L, 480L)

  fun getWakeHour(context: Context): Int = prefs(context).getInt(KEY_WAKE_HOUR, 8)

  fun getSleepHour(context: Context): Int = prefs(context).getInt(KEY_SLEEP_HOUR, 22)

  fun getDailyGoalMl(context: Context): Long = prefs(context).getLong(KEY_DAILY_GOAL_ML, 2500L)

  fun isWithinActiveHours(context: Context, currentHour: Int): Boolean {
    val wake = getWakeHour(context)
    val sleep = getSleepHour(context)
    return if (wake < sleep) {
      currentHour in wake until sleep
    } else if (wake > sleep) {
      currentHour >= wake || currentHour < sleep
    } else {
      true
    }
  }
}
