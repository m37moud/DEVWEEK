package dev.daymark.presentation.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.daymark.app.reducedMotionEnabled
import dev.daymark.history.HistoryMetric
import dev.daymark.history.HistoryMetrics
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str

private const val COUNTER_ANIMATION_DURATION_MS = 240
private const val PRESSED_SCALE_RATIO = 0.97f

@Composable
fun HistoryMetricsGrid(
    metrics: HistoryMetrics,
    onMetric: (HistoryMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HeroAttendanceCard(
            metrics = metrics,
            onClick = { onMetric(HistoryMetric.ATTENDANCE) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricKpiTile(
                metric = HistoryMetric.LATE,
                metrics = metrics,
                onClick = { onMetric(HistoryMetric.LATE) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricKpiTile(
                metric = HistoryMetric.ABSENT,
                metrics = metrics,
                onClick = { onMetric(HistoryMetric.ABSENT) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricKpiTile(
                metric = HistoryMetric.LEAVE_REST,
                metrics = metrics,
                onClick = { onMetric(HistoryMetric.LEAVE_REST) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricKpiTile(
                metric = HistoryMetric.HOLIDAY,
                metrics = metrics,
                onClick = { onMetric(HistoryMetric.HOLIDAY) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun HeroAttendanceCard(
    metrics: HistoryMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metric = HistoryMetric.ATTENDANCE
    val visuals = metricVisuals(metric)
    val count = metricCount(metric, metrics)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = reducedMotionEnabled()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) PRESSED_SCALE_RATIO else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "hero-tile-scale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .testTag("metric-${metric.name}")
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, visuals.borderColor),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricBadge(visuals = visuals)
                Text(
                    text = str(metric.metricLabel()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            AnimatedMetricCount(
                count = count,
                style = MaterialTheme.typography.headlineMedium,
                color = visuals.accentColor
            )
        }
    }
}

@Composable
private fun MetricKpiTile(
    metric: HistoryMetric,
    metrics: HistoryMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = metricVisuals(metric)
    val count = metricCount(metric, metrics)
    val detail = if (metric == HistoryMetric.LATE) lateDurationLabel(metrics.lateDuration) else null
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = reducedMotionEnabled()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) PRESSED_SCALE_RATIO else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "metric-tile-scale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .testTag("metric-${metric.name}")
            .heightIn(min = 84.dp)
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, visuals.borderColor),
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricBadge(visuals = visuals)
                AnimatedMetricCount(
                    count = count,
                    style = MaterialTheme.typography.titleLarge,
                    color = visuals.accentColor
                )
            }
            Text(
                text = str(metric.metricLabel()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = visuals.accentColor
                )
            }
        }
    }
}

@Composable
private fun MetricBadge(
    visuals: MetricVisuals,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = visuals.containerColor,
        contentColor = visuals.contentColor,
        modifier = modifier
    ) {
        Text(
            text = visuals.glyph,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun AnimatedMetricCount(
    count: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val appLanguage = LocalAppLanguage.current
    val reducedMotion = reducedMotionEnabled()
    val formattedCount = DisplayFormats.number(count, appLanguage)

    if (reducedMotion) {
        Text(
            text = formattedCount,
            style = style,
            color = color,
            modifier = modifier
        )
    } else {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val animationSpec = tween<Float>(
                    durationMillis = COUNTER_ANIMATION_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
                val offsetSpec = tween<IntOffset>(
                    durationMillis = COUNTER_ANIMATION_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
                if (targetState >= initialState) {
                    (slideInVertically(offsetSpec) { height -> height / 2 } + fadeIn(animationSpec)) togetherWith
                            (slideOutVertically(offsetSpec) { height -> -height / 2 } + fadeOut(
                                animationSpec
                            ))
                } else {
                    (slideInVertically(offsetSpec) { height -> -height / 2 } + fadeIn(animationSpec)) togetherWith
                            (slideOutVertically(offsetSpec) { height -> height / 2 } + fadeOut(
                                animationSpec
                            ))
                }
            },
            label = "metric-counter",
            modifier = modifier
        ) { targetCount ->
            Text(
                text = DisplayFormats.number(targetCount, appLanguage),
                style = style,
                color = color
            )
        }
    }
}

private fun metricCount(metric: HistoryMetric, metrics: HistoryMetrics): Int =
    when (metric) {
        HistoryMetric.ATTENDANCE -> metrics.attendance
        HistoryMetric.LATE -> metrics.late
        HistoryMetric.ABSENT -> metrics.absent
        HistoryMetric.LEAVE_REST -> metrics.leaveRest
        HistoryMetric.HOLIDAY -> metrics.holidays
    }

private data class MetricVisuals(
    val glyph: String,
    val containerColor: Color,
    val contentColor: Color,
    val accentColor: Color,
    val borderColor: Color
)

@Composable
private fun metricVisuals(metric: HistoryMetric): MetricVisuals =
    when (metric) {
        HistoryMetric.ATTENDANCE -> MetricVisuals(
            glyph = "✓",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            accentColor = MaterialTheme.colorScheme.primary,
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )

        HistoryMetric.LATE -> MetricVisuals(
            glyph = "!",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            accentColor = MaterialTheme.colorScheme.tertiary,
            borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
        )

        HistoryMetric.ABSENT -> MetricVisuals(
            glyph = "×",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            accentColor = MaterialTheme.colorScheme.error,
            borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        )

        HistoryMetric.LEAVE_REST -> MetricVisuals(
            glyph = "—",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            accentColor = MaterialTheme.colorScheme.secondary,
            borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
        )

        HistoryMetric.HOLIDAY -> MetricVisuals(
            glyph = "—",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            accentColor = MaterialTheme.colorScheme.outline,
            borderColor = MaterialTheme.colorScheme.outlineVariant
        )
    }
