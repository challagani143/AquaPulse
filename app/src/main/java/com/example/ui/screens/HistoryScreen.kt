package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DrinkType
import com.example.data.HydrationFormatters
import com.example.ui.DayHydrationSummary
import com.example.ui.HydrationDashboardData

@Composable
fun HistoryScreen(
  data: HydrationDashboardData,
  modifier: Modifier = Modifier,
) {
  val goalsMetCount = data.weeklySummaries.count { it.goalMet }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(
          text = "Hydration Insights",
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = "Your 7-day intake consistency and beverage breakdown",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Metric Cards Row
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          MetricCard(
            icon = Icons.Rounded.EmojiEvents,
            label = "Streak",
            value = "${data.currentStreakDays} days",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
          )
          MetricCard(
            icon = Icons.Rounded.WaterDrop,
            label = "Daily Avg",
            value = HydrationFormatters.formatVolume(data.averageDailyMl, data.unit),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
          )
          MetricCard(
            icon = Icons.Rounded.CheckCircle,
            label = "Goals Met",
            value = "$goalsMetCount / 7",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // 7-Day Bar Chart Card
      item {
        Card(
          shape = RoundedCornerShape(26.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Last 7 Days",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "Goal: ${HydrationFormatters.formatVolume(data.profile.dailyGoalMl, data.unit)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }

            Spacer(modifier = Modifier.height(20.dp))

            WeeklyHydrationChart(summaries = data.weeklySummaries)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              data.weeklySummaries.forEach { day ->
                Text(
                  text = day.dayLabel,
                  style = MaterialTheme.typography.labelMedium,
                  color =
                    if (day.goalMet) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      // Beverage Breakdown Card
      item {
        val totalAll = data.allLogs.sumOf { it.amountMl }.coerceAtLeast(1L)
        val byType =
          DrinkType.entries
            .map { type ->
              val sum = data.allLogs.filter { it.drinkType == type.key }.sumOf { it.amountMl }
              type to sum
            }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }

        Card(
          shape = RoundedCornerShape(26.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
              text = "Beverage Mix",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (byType.isEmpty()) {
              Text(
                text = "Log your first drink to see how water, tea, and electrolytes contribute to your hydration.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              byType.forEach { (type, amount) ->
                val fraction = (amount.toFloat() / totalAll.toFloat()).coerceIn(0f, 1f)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = drinkTypeIcon(type),
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = type.label,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                  Text(
                    text = HydrationFormatters.formatVolume(amount, data.unit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                  progress = { fraction },
                  color = MaterialTheme.colorScheme.primary,
                  trackColor = MaterialTheme.colorScheme.surfaceVariant,
                  modifier =
                    Modifier.fillMaxWidth()
                      .height(8.dp)
                      .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.height(12.dp))
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun MetricCard(
  icon: ImageVector,
  label: String,
  value: String,
  tint: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun WeeklyHydrationChart(summaries: List<DayHydrationSummary>) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  val goalLineColor = MaterialTheme.colorScheme.tertiary

  Canvas(modifier = Modifier.fillMaxWidth().height(168.dp)) {
    if (summaries.isEmpty()) return@Canvas
    val maxVal =
      summaries
        .maxOf { maxOf(it.totalMl, it.goalMl) }
        .coerceAtLeast(1000L)
        .toFloat() * 1.1f

    val count = summaries.size
    val slotWidth = size.width / count.toFloat()
    val barWidth = (slotWidth * 0.46f).coerceAtMost(32.dp.toPx())
    val cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())

    val goalRatio = (summaries.first().goalMl.toFloat() / maxVal).coerceIn(0f, 1f)
    val goalY = size.height * (1f - goalRatio)

    drawLine(
      color = goalLineColor.copy(alpha = 0.7f),
      start = Offset(0f, goalY),
      end = Offset(size.width, goalY),
      strokeWidth = 2.dp.toPx(),
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
    )

    summaries.forEachIndexed { index, day ->
      val left = index * slotWidth + (slotWidth - barWidth) / 2f
      drawRoundRect(
        color = trackColor.copy(alpha = 0.55f),
        topLeft = Offset(left, 0f),
        size = Size(barWidth, size.height),
        cornerRadius = cornerRadius,
      )

      val ratio = (day.totalMl.toFloat() / maxVal).coerceIn(0f, 1f)
      if (ratio > 0.01f) {
        val barHeight = size.height * ratio
        val top = size.height - barHeight
        drawRoundRect(
          brush =
            if (day.goalMet) {
              Brush.verticalGradient(listOf(secondaryColor, primaryColor))
            } else {
              Brush.verticalGradient(
                listOf(primaryColor.copy(alpha = 0.65f), primaryColor.copy(alpha = 0.45f))
              )
            },
          topLeft = Offset(left, top),
          size = Size(barWidth, barHeight),
          cornerRadius = cornerRadius,
        )
      }
    }
  }
}
