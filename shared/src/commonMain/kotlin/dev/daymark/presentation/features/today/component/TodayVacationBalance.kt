package dev.daymark.presentation.features.today.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.daymark.app.reducedMotionEnabled
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.VacationBalanceSummary
import dev.daymark.presentation.component.SummaryRow
import dev.daymark.presentation.features.today.TodayVacationBalanceUiState
import dev.daymark.resources.Res
import dev.daymark.resources.edit
import dev.daymark.resources.vacation_balance
import dev.daymark.resources.vacation_balance_load_failed
import dev.daymark.resources.vacation_balance_original
import dev.daymark.resources.vacation_balance_remaining
import dev.daymark.resources.vacation_balance_used
import dev.daymark.resources.vacation_balance_year
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str

private const val LOW_BALANCE_THRESHOLD_DAYS = 3
private const val LOW_BALANCE_RATIO_CEILING = 0.2f
private const val PRESSED_SCALE_RATIO = 0.92f
private val GAUGE_SIZE: Dp = 48.dp
private val GAUGE_STROKE_WIDTH: Dp = 4.dp

private data class VacationProgressColors(
    val progress: Color,
    val track: Color
)

@Composable
fun TodayVacationBalance(
    state: TodayVacationBalanceUiState,
    onSave: (String, String?) -> Unit
) {
    val summary = state.summary
    val year = summary?.year
    var adjusting by rememberSaveable(year) { mutableStateOf(false) }
    var submittedAtRevision by rememberSaveable(year) { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.saveRevision) {
        val submitted = submittedAtRevision
        if (submitted != null && state.saveRevision > submitted) {
            adjusting = false
            submittedAtRevision = null
        }
    }

    if (state.loadFailed) {
        VacationBalanceLoadErrorCard()
    }
    if (summary == null) return

    TodayVacationBalanceCard(
        summary = summary,
        onAdjustBalance = {
            submittedAtRevision = null
            adjusting = true
        }
    )
    if (adjusting) {
        BalanceAdjustDialog(
            state = BalanceAdjustUiState(
                initialBalance = summary.remainingDays,
                year = summary.year,
                busy = state.saving,
                saveFailure = state.saveResult?.takeIf { mutation ->
                    submittedAtRevision != null && !state.saving && mutation != PlanMutationResult.Applied
                }
            ),
            onSave = { text, note ->
                submittedAtRevision = state.saveRevision
                onSave(text, note)
            },
            onDismiss = { adjusting = false }
        )
    }
}

@Composable
private fun VacationBalanceLoadErrorCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = str(Res.string.vacation_balance_load_failed),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun TodayVacationBalanceCard(
    summary: VacationBalanceSummary,
    onAdjustBalance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val original = summary.originalDays
    val remaining = summary.remainingDays
    val ratio = calculateBalanceRatio(original = original, remaining = remaining)
    val colors = resolveBalanceColors(original = original, remaining = remaining)
    val reducedMotion = reducedMotionEnabled()

    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "VacationBalanceProgress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("today-vacation-balance"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .animateContentSize(
                    animationSpec = if (reducedMotion) snap() else spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            VacationBalanceMiniGauge(
                remaining = remaining,
                animatedRatio = animatedRatio,
                colors = colors
            )
            VacationBalanceInfoColumn(
                year = summary.year,
                remaining = remaining,
                used = summary.usedDays,
                original = original,
                modifier = Modifier.weight(1f)
            )
            VacationEditActionButton(onAdjustBalance = onAdjustBalance)
        }
    }
}

@Composable
private fun VacationBalanceMiniGauge(
    remaining: Int,
    animatedRatio: Float,
    colors: VacationProgressColors,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(GAUGE_SIZE),
        contentAlignment = Alignment.Center
    ) {
        VacationProgressArc(
            animatedRatio = animatedRatio,
            colors = colors,
            modifier = Modifier.fillMaxSize()
        )
        AnimatedNumberTicker(
            value = remaining,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.progress
        )
    }
}

@Composable
private fun VacationBalanceInfoColumn(
    year: Int?,
    remaining: Int,
    used: Int,
    original: Int,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = year?.let {
                str(
                    Res.string.vacation_balance_year,
                    isolated(DisplayFormats.number(it, language))
                )
            } ?: str(Res.string.vacation_balance),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        SummaryRow(
            label = str(Res.string.vacation_balance_original),
            value = isolated(DisplayFormats.number(original, language))
        )


        SummaryRow(
            label = str(Res.string.vacation_balance_used),
            value = isolated(DisplayFormats.number(used, language))
        )


    }
}



@Composable
private fun VacationEditActionButton(
    onAdjustBalance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = reducedMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) PRESSED_SCALE_RATIO else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "VacationEditScale"
    )

    FilledTonalIconButton(
        onClick = onAdjustBalance,
        modifier = modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = str(Res.string.edit),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun VacationProgressArc(
    animatedRatio: Float,
    colors: VacationProgressColors,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokePx = GAUGE_STROKE_WIDTH.toPx()
        val halfStroke = strokePx / 2f
        val arcSize = Size(
            width = size.width - strokePx,
            height = size.height - strokePx
        )
        val topLeft = Offset(x = halfStroke, y = halfStroke)
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

        drawArc(
            color = colors.track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        if (animatedRatio > 0.001f) {
            drawArc(
                color = colors.progress,
                startAngle = -90f,
                sweepAngle = (360f * animatedRatio).coerceIn(0f, 360f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
        }
    }
}

@Composable
private fun AnimatedNumberTicker(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val reducedMotion = reducedMotionEnabled()
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(animationSpec = snap()).togetherWith(fadeOut(animationSpec = snap()))
            } else if (targetState > initialState) {
                (slideInVertically { height -> height } + fadeIn())
                    .togetherWith(slideOutVertically { height -> -height } + fadeOut())
            } else {
                (slideInVertically { height -> -height } + fadeIn())
                    .togetherWith(slideOutVertically { height -> height } + fadeOut())
            }
        },
        label = "AnimatedNumberTicker",
        modifier = modifier
    ) { targetValue ->
        Text(
            text = isolated(DisplayFormats.number(targetValue, LocalAppLanguage.current)),
            style = style,
            color = color
        )
    }
}

@Composable
private fun formatBalanceSubtitle(
    remaining: Int,
    used: Int,
    original: Int,
    language: AppLanguage
): String {
    val remText = isolated(DisplayFormats.number(remaining, language))
    val usedText = isolated(DisplayFormats.number(used, language))
    val origText = isolated(DisplayFormats.number(original, language))
    val remLabel = str(Res.string.vacation_balance_remaining)
    val usedLabel = str(Res.string.vacation_balance_used)

    return "$remLabel $remText · $usedLabel $usedText / $origText"
}

private fun calculateBalanceRatio(original: Int, remaining: Int): Float {
    return if (original > 0) {
        (remaining.toFloat() / original.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
}

@Composable
private fun resolveBalanceColors(original: Int, remaining: Int): VacationProgressColors {
    val ratio = calculateBalanceRatio(original = original, remaining = remaining)
    val isDepleted = remaining <= 0
    val isLow =
        !isDepleted && (remaining <= LOW_BALANCE_THRESHOLD_DAYS || (original > 0 && ratio <= LOW_BALANCE_RATIO_CEILING))

    val targetProgressColor = when {
        isDepleted -> MaterialTheme.colorScheme.error
        isLow -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val targetTrackColor = when {
        isDepleted -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        isLow -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val progressColor by animateColorAsState(
        targetValue = targetProgressColor,
        label = "VacationProgressColor"
    )
    val trackColor by animateColorAsState(
        targetValue = targetTrackColor,
        label = "VacationTrackColor"
    )

    return VacationProgressColors(
        progress = progressColor,
        track = trackColor
    )
}
