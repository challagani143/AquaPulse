package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class HydrationReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (!ReminderPreferences.isEnabled(context)) return

    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    if (ReminderPreferences.isWithinActiveHours(context, currentHour)) {
      NotificationHelper.showHydrationReminder(context, isTest = false)
    }

    val intervalMinutes = ReminderPreferences.getIntervalMinutes(context)
    ReminderScheduler.schedule(context, intervalMinutes)
  }
}
