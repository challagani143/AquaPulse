package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
  const val CHANNEL_ID = "hydration_reminders"
  const val NOTIFICATION_ID = 1001
  const val ACTION_QUICK_LOG = "com.example.action.QUICK_LOG_WATER"
  const val EXTRA_AMOUNT_ML = "extra_amount_ml"

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(NotificationManager::class.java)
      if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = context.getString(R.string.notification_channel_desc)
            enableVibration(true)
          }
        manager?.createNotificationChannel(channel)
      }
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
  }

  fun showHydrationReminder(context: Context, isTest: Boolean = false) {
    if (!hasNotificationPermission(context)) return
    ensureChannel(context)

    val openIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        context,
        0,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val log250Intent =
      PendingIntent.getBroadcast(
        context,
        250,
        Intent(context, HydrationActionReceiver::class.java).apply {
          action = ACTION_QUICK_LOG
          putExtra(EXTRA_AMOUNT_ML, 250L)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val log500Intent =
      PendingIntent.getBroadcast(
        context,
        500,
        Intent(context, HydrationActionReceiver::class.java).apply {
          action = ACTION_QUICK_LOG
          putExtra(EXTRA_AMOUNT_ML, 500L)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val title =
      if (isTest) {
        context.getString(R.string.notification_test_title)
      } else {
        context.getString(R.string.notification_reminder_title)
      }
    val body =
      if (isTest) {
        context.getString(R.string.notification_test_body)
      } else {
        context.getString(R.string.notification_reminder_body)
      }

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_water_drop)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setContentIntent(contentPendingIntent)
        .addAction(0, "+250 ml", log250Intent)
        .addAction(0, "+500 ml", log500Intent)
        .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }

  fun showQuickLogConfirmation(context: Context, amountMl: Long) {
    if (!hasNotificationPermission(context)) return
    ensureChannel(context)

    val openIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        context,
        1,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_water_drop)
        .setContentTitle("Logged +${amountMl} ml")
        .setContentText("Your AquaPulse hydration log is synced.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(contentPendingIntent)
        .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }
}
