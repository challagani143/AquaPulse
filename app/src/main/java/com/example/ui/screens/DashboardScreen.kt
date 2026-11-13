package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DrinkType
import com.example.data.HydrationDateUtils
import com.example.data.HydrationFormatters
import com.example.data.PreferredUnit
import com.example.data.WaterLog
import com.example.notifications.NotificationHelper
import com.example.ui.HydrationDashboardData
import com.example.ui.components.HydrationWaveGauge
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
  data: HydrationDashboardData,
  isMutating: Boolean,
  onLogDrink: (Long, DrinkType, String) -> Unit,
  onDeleteLog: (String) -> Unit,
  onNavigateToReminders: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var hasNotifPermission by remember {
    mutableStateOf(NotificationHelper.hasNotificationPermission(context))
  }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotifPermission = granted
    }

  var showCustomSheet by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Header Row
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "Today's Hydration",
              style = MaterialTheme.typography.headlineLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text =
                if (data.remainingMl == 0L) "Daily goal complete!"
                else "${HydrationFormatters.formatVolume(data.remainingMl, data.unit)} left to reach goal",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = "Hydration streak",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${data.currentStreakDays}d streak",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
              )
            }
          }
        }
      }

      // Notification Permission Prompt Banner (Android 13+)
      if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        item {
          Card(
            shape = RoundedCornerShape(22.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
              ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Turn on hydration reminders",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                  text = "Get gentle push nudges with one-tap water logging.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.testTag("enable_notifications_banner_button"),
              ) {
                Text("Allow")
              }
            }
          }
        }
      }

      // Main Tank Hero Card
      item {
        Card(
          shape = RoundedCornerShape(28.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .background(
                  Brush.verticalGradient(
                    colors =
                      listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f),
                      )
                  )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            HydrationWaveGauge(
              todayTotalMl = data.todayTotalMl,
              dailyGoalMl = data.profile.dailyGoalMl,
              unit = data.unit,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reminder Status Pill
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier =
                Modifier.clip(RoundedCornerShape(14.dp))
                  .clickable(onClick = onNavigateToReminders)
                  .testTag("reminder_status_pill"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              ) {
                Icon(
                  imageVector =
                    if (data.profile.notificationsEnabled && hasNotifPermission) {
                      Icons.Rounded.NotificationsActive
                    } else {
                      Icons.Rounded.NotificationsOff
                    },
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text =
                    if (data.profile.notificationsEnabled && hasNotifPermission) {
                      "Reminders ${HydrationFormatters.formatInterval(data.profile.reminderIntervalMinutes).lowercase()}"
                    } else {
                      "Reminders paused — tap to configure"
                    },
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      // Quick Log Section
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Quick Log",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          TextButton(
            onClick = { showCustomSheet = true },
            modifier = Modifier.testTag("custom_sip_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Custom drink")
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          QuickLogCard(
            amountMl = 150L,
            label = "Small",
            unit = data.unit,
            enabled = !isMutating,
            onClick = { onLogDrink(150L, DrinkType.WATER, "Small cup") },
            modifier = Modifier.weight(1f).testTag("quick_add_150"),
          )
          QuickLogCard(
            amountMl = 250L,
            label = "Glass",
            unit = data.unit,
            enabled = !isMutating,
            onClick = { onLogDrink(250L, DrinkType.WATER, "Glass of water") },
            modifier = Modifier.weight(1f).testTag("quick_add_250"),
          )
          QuickLogCard(
            amountMl = 500L,
            label = "Bottle",
            unit = data.unit,
            enabled = !isMutating,
            onClick = { onLogDrink(500L, DrinkType.WATER, "Water bottle") },
            modifier = Modifier.weight(1f).testTag("quick_add_500"),
          )
        }
      }

      // Today's Timeline Header
      item {
        Text(
          text = "Today's Log (${data.todayLogs.size})",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.padding(top = 4.dp),
        )
      }

      if (data.todayLogs.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Image(
                painter = painterResource(id = R.drawable.img_empty_hydration),
                contentDescription = "Empty hydration glass carafe illustration",
                contentScale = ContentScale.Crop,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(18.dp)),
              )
              Spacer(modifier = Modifier.height(14.dp))
              Text(
                text = stringResource(R.string.empty_logs_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = stringResource(R.string.empty_logs_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(data.todayLogs, key = { it.id }) { log ->
          WaterLogRowCard(
            log = log,
            unit = data.unit,
            onDelete = { onDeleteLog(log.id) },
          )
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }

  if (showCustomSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedType by remember { mutableStateOf(DrinkType.WATER) }
    var sliderAmount by remember { mutableFloatStateOf(250f) }
    var noteText by remember { mutableStateOf("") }

    ModalBottomSheet(
      onDismissRequest = { showCustomSheet = false },
      sheetState = sheetState,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
      ) {
        Text(
          text = "Log Custom Drink",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Beverage Type",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DrinkType.entries.forEach { type ->
            FilterChip(
              selected = selectedType == type,
              onClick = {
                selectedType = type
                sliderAmount = type.defaultAmountMl.toFloat()
              },
              label = { Text(type.label) },
              leadingIcon = {
                Icon(
                  imageVector = drinkTypeIcon(type),
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val roundedMl = ((sliderAmount / 25f).roundToLong() * 25L).coerceIn(50L, 1200L)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Amount",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = HydrationFormatters.formatVolume(roundedMl, data.unit),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        Slider(
          value = sliderAmount,
          onValueChange = { sliderAmount = it },
          valueRange = 50f..1200f,
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = noteText,
          onValueChange = { if (it.length <= 80) noteText = it },
          label = { Text("Note (optional)") },
          placeholder = { Text("e.g., Post-run bottle") },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
          onClick = {
            onLogDrink(roundedMl, selectedType, noteText.trim())
            showCustomSheet = false
          },
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("confirm_custom_log_button"),
        ) {
          Text(
            text = "Log ${HydrationFormatters.formatVolume(roundedMl, data.unit)}",
            style = MaterialTheme.typography.labelLarge,
          )
        }
        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }
}

@Composable
private fun QuickLogCard(
  amountMl: Long,
  label: String,
  unit: PreferredUnit,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(22.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
      ),
    modifier = modifier.minimumInteractiveComponentSize(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = Icons.Rounded.WaterDrop,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "+${HydrationFormatters.formatVolume(amountMl, unit)}",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
      )
    }
  }
}

@Composable
private fun WaterLogRowCard(
  log: WaterLog,
  unit: PreferredUnit,
  onDelete: () -> Unit,
) {
  val drinkType = DrinkType.fromKey(log.drinkType)
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth().testTag("water_log_card_${log.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = drinkTypeIcon(drinkType),
          contentDescription = drinkType.label,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = HydrationFormatters.formatVolume(log.amountMl, unit),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        val subtitle =
          buildString {
            append(drinkType.label)
            append(" • ")
            append(HydrationDateUtils.formatTime(log.loggedAtMillis))
            if (log.note.isNotBlank()) {
              append(" • ")
              append(log.note)
            }
          }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_log_${log.id}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "Delete log entry",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

fun drinkTypeIcon(type: DrinkType): ImageVector =
  when (type) {
    DrinkType.WATER -> Icons.Rounded.WaterDrop
    DrinkType.SPARKLING -> Icons.Rounded.LocalDrink
    DrinkType.TEA -> Icons.Rounded.LocalCafe
    DrinkType.ELECTROLYTES -> Icons.Rounded.Bolt
    DrinkType.COFFEE -> Icons.Rounded.Coffee
  }
