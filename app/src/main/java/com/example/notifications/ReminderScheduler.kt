package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object ReminderScheduler {
  private const val REQUEST_CODE = 4201

  private fun reminderPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context.applicationContext, HydrationReminderReceiver::class.java)
    return PendingIntent.getBroadcast(
      context.applicationContext,
      REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  fun schedule(context: Context, intervalMinutes: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intervalMs = intervalMinutes.coerceIn(30L, 480L) * 60_000L
    val triggerAt = System.currentTimeMillis() + intervalMs
    val pendingIntent = reminderPendingIntent(context)
    alarmManager.cancel(pendingIntent)
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
  }

  fun cancel(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    alarmManager.cancel(reminderPendingIntent(context))
  }

  fun syncSchedule(
    context: Context,
    enabled: Boolean,
    intervalMinutes: Long,
    wakeHour: Int,
    sleepHour: Int,
    dailyGoalMl: Long,
  ) {
    ReminderPreferences.saveSchedule(
      context = context,
      enabled = enabled,
      intervalMinutes = intervalMinutes,
      wakeHour = wakeHour,
      sleepHour = sleepHour,
      dailyGoalMl = dailyGoalMl,
    )
    if (enabled && NotificationHelper.hasNotificationPermission(context)) {
      schedule(context, intervalMinutes)
    } else {
      cancel(context)
    }
  }
}
