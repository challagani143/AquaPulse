package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.HydrationFormatters
import com.example.data.PreferredUnit
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun HydrationWaveGauge(
  todayTotalMl: Long,
  dailyGoalMl: Long,
  unit: PreferredUnit,
  modifier: Modifier = Modifier,
) {
  val targetFraction =
    (todayTotalMl.toFloat() / dailyGoalMl.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
  val animatedProgress by
    animateFloatAsState(
      targetValue = targetFraction,
      animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow),
      label = "hydration_progress",
    )

  val infiniteTransition = rememberInfiniteTransition(label = "wave_transition")
  val wavePhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 3200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "wave_phase",
    )

  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  val surfaceBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
  val percent = ((todayTotalMl.toFloat() / dailyGoalMl.coerceAtLeast(1L)) * 100f).toInt()

  Box(modifier = modifier.size(224.dp), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokeWidth = 14.dp.toPx()
      val radius = (size.minDimension - strokeWidth * 2f) / 2f
      val center = Offset(size.width / 2f, size.height / 2f)

      // Inner circle background
      drawCircle(color = surfaceBg, radius = radius - strokeWidth * 0.35f, center = center)

      // Clip liquid wave inside inner circle
      val innerRadius = radius - strokeWidth * 0.55f
      val circlePath =
        Path().apply {
          addOval(
            androidx.compose.ui.geometry.Rect(
              center.x - innerRadius,
              center.y - innerRadius,
              center.x + innerRadius,
              center.y + innerRadius,
            )
          )
        }

      clipPath(circlePath) {
        if (animatedProgress > 0.005f) {
          val bottomY = center.y + innerRadius
          val topY = center.y - innerRadius
          val waterLevelY = bottomY - (bottomY - topY) * animatedProgress
          val waveAmplitude =
            if (animatedProgress >= 0.99f) 2.dp.toPx() else 7.dp.toPx()

          // Back wave (lighter)
          val backWave = Path().apply {
            moveTo(center.x - innerRadius, bottomY)
            var x = center.x - innerRadius
            val endX = center.x + innerRadius
            while (x <= endX) {
              val relX = (x - (center.x - innerRadius)) / (innerRadius * 2f)
              val y =
                waterLevelY +
                  sin((relX * 2.5f * PI).toFloat() + wavePhase + 1.4f) * waveAmplitude
              lineTo(x, y)
              x += 4f
            }
            lineTo(endX, bottomY)
            close()
          }
          drawPath(
            path = backWave,
            color = secondaryColor.copy(alpha = 0.28f),
          )

          // Front wave (gradient)
          val frontWave = Path().apply {
            moveTo(center.x - innerRadius, bottomY)
            var x = center.x - innerRadius
            val endX = center.x + innerRadius
            while (x <= endX) {
              val relX = (x - (center.x - innerRadius)) / (innerRadius * 2f)
              val y =
                waterLevelY +
                  sin((relX * 2.2f * PI).toFloat() - wavePhase) * waveAmplitude
              lineTo(x, y)
              x += 4f
            }
            lineTo(endX, bottomY)
            close()
          }
          drawPath(
            path = frontWave,
            brush =
              Brush.verticalGradient(
                colors = listOf(secondaryColor.copy(alpha = 0.52f), primaryColor.copy(alpha = 0.72f)),
                startY = waterLevelY - waveAmplitude,
                endY = bottomY,
              ),
          )
        }
      }

      // Outer progress track
      drawCircle(
        color = trackColor,
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth),
      )

      // Outer active arc
      drawArc(
        brush = Brush.sweepGradient(listOf(primaryColor, secondaryColor, primaryColor)),
        startAngle = -90f,
        sweepAngle = 360f * animatedProgress,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
      )
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "$percent%",
        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
        color =
          if (animatedProgress > 0.52f) Color.White
          else MaterialTheme.colorScheme.onBackground,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = HydrationFormatters.formatVolume(todayTotalMl, unit),
        style = MaterialTheme.typography.titleMedium,
        color =
          if (animatedProgress > 0.48f) Color.White.copy(alpha = 0.92f)
          else MaterialTheme.colorScheme.primary,
      )
      Text(
        text = "of ${HydrationFormatters.formatVolume(dailyGoalMl, unit)}",
        style = MaterialTheme.typography.labelMedium,
        color =
          if (animatedProgress > 0.44f) Color.White.copy(alpha = 0.80f)
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
