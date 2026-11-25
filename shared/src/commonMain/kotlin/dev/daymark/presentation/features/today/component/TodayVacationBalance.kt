package dev.daymark.presentation.features.today.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.VacationBalanceSummary
import dev.daymark.presentation.features.today.TodayVacationBalanceUiState
import dev.daymark.resources.Res
import dev.daymark.resources.edit
import dev.daymark.resources.vacation_balance
import dev.daymark.resources.vacation_balance_load_failed
import dev.daymark.resources.vacation_balance_original
import dev.daymark.resources.vacation_balance_remaining
import dev.daymark.resources.vacation_balance_used
import dev.daymark.resources.vacation_balance_year
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str

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
        Card(
            modifier = Modifier.fillMaxWidth(),
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
fun TodayVacationBalanceCard(
    summary: VacationBalanceSummary,
    onAdjustBalance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val original = summary.originalDays
    val remaining = summary.remainingDays
    val used = summary.usedDays
    val ratio = if (original > 0) {
        (remaining.toFloat() / original.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = spring(
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = summary.year?.let { year ->
                            str(
                                Res.string.vacation_balance_year,
                                isolated(DisplayFormats.number(year, LocalAppLanguage.current))
                            )
                        } ?: str(Res.string.vacation_balance),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                FilledTonalIconButton(
                    onClick = onAdjustBalance,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = str(Res.string.edit)
                    )

                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = str(Res.string.vacation_balance_remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedNumberTicker(
                    value = remaining,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { animatedRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SubStatTile(
                    label = str(Res.string.vacation_balance_original),
                    value = original,
                    modifier = Modifier.weight(1f)
                )
                SubStatTile(
                    label = str(Res.string.vacation_balance_used),
                    value = used,
                    modifier = Modifier.weight(1f)
                )
            }
//
//            OutlinedButton(
//                onClick = onAdjustBalance,
//                modifier = Modifier.fillMaxWidth(),
//                shape = MaterialTheme.shapes.small
//            ) {
//                Icon(
//                    imageVector = Icons.Default.Edit,
//                    contentDescription = null,
//                    modifier = Modifier.size(18.dp)
//                )
//                Spacer(modifier = Modifier.size(8.dp))
//                Text(
//                    text = str(Res.string.adjust_balance),
//                    style = MaterialTheme.typography.labelLarge
//                )
//            }
        }
    }
}

@Composable
private fun SubStatTile(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedNumberTicker(
                value = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AnimatedNumberTicker(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (targetState > initialState) {
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
