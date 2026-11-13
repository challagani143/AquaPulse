package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.DrinkType
import com.example.data.HydrationDateUtils
import com.example.data.HydrationRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HydrationActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != NotificationHelper.ACTION_QUICK_LOG) return
    val amountMl = intent.getLongExtra(NotificationHelper.EXTRA_AMOUNT_ML, 250L).coerceIn(10L, 5000L)
    val uid = Firebase.auth.currentUser?.uid ?: return

    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val repo = HydrationRepository(context.applicationContext)
        val now = System.currentTimeMillis()
        val result =
          repo.addWaterLog(
            userId = uid,
            amountMl = amountMl,
            drinkType = DrinkType.WATER.key,
            dateKey = HydrationDateUtils.dateKeyForMillis(now),
            loggedAtMillis = now,
            note = "Quick-logged from reminder",
          )
        if (result.isSuccess) {
          NotificationHelper.showQuickLogConfirmation(context.applicationContext, amountMl)
        }
      } finally {
        pendingResult.finish()
      }
    }
  }
}
