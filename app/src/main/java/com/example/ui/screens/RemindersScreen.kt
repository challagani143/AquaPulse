package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.auth.signOutUser
import com.example.data.HydrationFormatters
import com.example.data.PreferredUnit
import com.example.notifications.NotificationHelper
import com.example.ui.HydrationDashboardData
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemindersScreen(
  data: HydrationDashboardData,
  onSaveProfile: (Long, PreferredUnit, Boolean, Long, Int, Int, String?) -> Unit,
  onTestNotificationSent: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var hasPermission by remember {
    mutableStateOf(NotificationHelper.hasNotificationPermission(context))
  }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
      if (granted) {
        NotificationHelper.showHydrationReminder(context, isTest = true)
        onTestNotificationSent("Notifications enabled! Test reminder sent.")
      }
    }

  val profile = data.profile
  var goalSlider by remember(profile.dailyGoalMl) { mutableFloatStateOf(profile.dailyGoalMl.toFloat()) }
  val intervals = listOf(30L, 60L, 90L, 120L, 180L, 240L)
  val userEmail = Firebase.auth.currentUser?.email ?: "Signed in with Google"

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(
          text = "Reminders & Goals",
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = "Customize push hydration alerts, quiet hours, and daily targets",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Permission status card when denied
      if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        item {
          Card(
            shape = RoundedCornerShape(24.dp),
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.NotificationsNone,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Notification permission required",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Allow AquaPulse to send scheduled hydration reminders and quick-log actions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
              )
              Spacer(modifier = Modifier.height(12.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                  onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                  modifier = Modifier.testTag("grant_notification_permission_button"),
                ) {
                  Text("Grant permission")
                }
                OutlinedButton(
                  onClick = {
                    val intent =
                      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                      }
                    context.startActivity(intent)
                  }
                ) {
                  Text("App settings")
                }
              }
            }
          }
        }
      }

      // Push Reminder Schedule Card
      item {
        Card(
          shape = RoundedCornerShape(26.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                  imageVector = Icons.Rounded.NotificationsActive,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = "Hydration Push Reminders",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text =
                      if (profile.notificationsEnabled) {
                        HydrationFormatters.formatInterval(profile.reminderIntervalMinutes)
                      } else {
                        "Notifications turned off"
                      },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              Switch(
                checked = profile.notificationsEnabled,
                onCheckedChange = { enabled ->
                  if (
                    enabled &&
                      !hasPermission &&
                      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                  ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                  onSaveProfile(
                    profile.dailyGoalMl,
                    data.unit,
                    enabled,
                    profile.reminderIntervalMinutes,
                    profile.wakeHour.toInt(),
                    profile.sleepHour.toInt(),
                    if (enabled) "Reminders scheduled" else "Reminders paused",
                  )
                },
                modifier = Modifier.testTag("notifications_enabled_switch"),
              )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Reminder Frequency",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              intervals.forEach { mins ->
                FilterChip(
                  selected = profile.reminderIntervalMinutes == mins,
                  onClick = {
                    onSaveProfile(
                      profile.dailyGoalMl,
                      data.unit,
                      profile.notificationsEnabled,
                      mins,
                      profile.wakeHour.toInt(),
                      profile.sleepHour.toInt(),
                      "Interval set to ${HydrationFormatters.formatInterval(mins).lowercase()}",
                    )
                  },
                  label = { Text(HydrationFormatters.formatInterval(mins).removePrefix("Every ")) },
                  modifier = Modifier.testTag("interval_chip_$mins"),
                )
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              HourStepperCard(
                icon = Icons.Rounded.WbSunny,
                title = "Start at",
                hour = profile.wakeHour.toInt(),
                onChange = { nextHour ->
                  onSaveProfile(
                    profile.dailyGoalMl,
                    data.unit,
                    profile.notificationsEnabled,
                    profile.reminderIntervalMinutes,
                    nextHour,
                    profile.sleepHour.toInt(),
                    "Active window updated",
                  )
                },
                modifier = Modifier.weight(1f),
              )
              HourStepperCard(
                icon = Icons.Rounded.Bedtime,
                title = "Quiet after",
                hour = profile.sleepHour.toInt(),
                onChange = { nextHour ->
                  onSaveProfile(
                    profile.dailyGoalMl,
                    data.unit,
                    profile.notificationsEnabled,
                    profile.reminderIntervalMinutes,
                    profile.wakeHour.toInt(),
                    nextHour,
                    "Quiet hours updated",
                  )
                },
                modifier = Modifier.weight(1f),
              )
            }

            Spacer(modifier = Modifier.height(18.dp))

            FilledTonalButton(
              onClick = {
                if (
                  !NotificationHelper.hasNotificationPermission(context) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ) {
                  permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                  NotificationHelper.showHydrationReminder(context, isTest = true)
                  onTestNotificationSent("Test push notification sent! Check your status bar.")
                }
              },
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.fillMaxWidth().height(50.dp).testTag("test_notification_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Send Test Push Notification")
            }
          }
        }
      }

      // Daily Goal & Units Card
      item {
        Card(
          shape = RoundedCornerShape(26.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Daily Goal & Units",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val pendingGoal = ((goalSlider / 100f).roundToLong() * 100L).coerceIn(1000L, 5000L)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Daily target",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = HydrationFormatters.formatGoalVolume(pendingGoal, data.unit),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
              )
            }

            Slider(
              value = goalSlider,
              onValueChange = { goalSlider = it },
              onValueChangeFinished = {
                val snapped = ((goalSlider / 100f).roundToLong() * 100L).coerceIn(1000L, 5000L)
                onSaveProfile(
                  snapped,
                  data.unit,
                  profile.notificationsEnabled,
                  profile.reminderIntervalMinutes,
                  profile.wakeHour.toInt(),
                  profile.sleepHour.toInt(),
                  "Daily goal updated to ${HydrationFormatters.formatVolume(snapped, data.unit)}",
                )
              },
              valueRange = 1000f..5000f,
              modifier = Modifier.fillMaxWidth().testTag("daily_goal_slider"),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              PreferredUnit.entries.forEach { unitOption ->
                FilterChip(
                  selected = data.unit == unitOption,
                  onClick = {
                    onSaveProfile(
                      profile.dailyGoalMl,
                      unitOption,
                      profile.notificationsEnabled,
                      profile.reminderIntervalMinutes,
                      profile.wakeHour.toInt(),
                      profile.sleepHour.toInt(),
                      "Units set to ${unitOption.label}",
                    )
                  },
                  label = { Text(unitOption.label) },
                  modifier = Modifier.weight(1f).testTag("unit_chip_${unitOption.key}"),
                )
              }
            }
          }
        }
      }

      // Account Card
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Cloud Sync Active",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = userEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            OutlinedButton(
              onClick = { signOutUser(credentialManager = credentialManager, scope = scope) },
              modifier = Modifier.testTag("sign_out_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Sign out")
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun HourStepperCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  hour: Int,
  onChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = { onChange((hour + 23) % 24) },
          modifier = Modifier.size(36.dp).minimumInteractiveComponentSize(),
        ) {
          Text("-", style = MaterialTheme.typography.titleLarge)
        }
        Text(
          text = HydrationFormatters.formatHour(hour),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(
          onClick = { onChange((hour + 1) % 24) },
          modifier = Modifier.size(36.dp).minimumInteractiveComponentSize(),
        ) {
          Text("+", style = MaterialTheme.typography.titleLarge)
        }
      }
    }
  }
}
