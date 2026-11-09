package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.daymark.app.HostPlatform
import dev.daymark.resources.Res
import dev.daymark.resources.add_widget_to_home
import dev.daymark.resources.check_attendance
import dev.daymark.resources.onboarding_widget_title
import dev.daymark.resources.status_awaiting
import dev.daymark.resources.status_day_off
import dev.daymark.resources.status_late
import dev.daymark.resources.status_present
import dev.daymark.resources.widget_help_android
import dev.daymark.resources.widget_help_desktop
import dev.daymark.resources.widget_help_ios
import dev.daymark.resources.widget_pin_requested
import dev.daymark.resources.widget_preview_header
import dev.daymark.settings.str
import dev.daymark.widgets.isPinWidgetSupported
import dev.daymark.widgets.pinWidgetToHomeScreen

internal enum class WidgetPreviewScenario {
    READY,
    PRESENT,
    LATE,
    DAY_OFF
}

@Composable
internal fun SettingsWidgetsCard(
    modifier: Modifier = Modifier,
    platform: HostPlatform = HostPlatform.ANDROID,
) {
    if (platform == HostPlatform.TEST) return

    var pinRequested by remember { mutableStateOf(false) }
    var selectedScenario by remember { mutableStateOf(WidgetPreviewScenario.READY) }
    val canPin = isPinWidgetSupported()
    val helpTextRes = when (platform) {
        HostPlatform.ANDROID -> Res.string.widget_help_android
        HostPlatform.IOS -> Res.string.widget_help_ios
        HostPlatform.TEST -> Res.string.widget_help_desktop
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WidgetHeader()

            ScenarioSwitcher(
                selected = selectedScenario,
                onSelect = { selectedScenario = it }
            )

            WidgetPreviewMockup(
                scenario = selectedScenario,
                onFastRegister = { selectedScenario = WidgetPreviewScenario.PRESENT }
            )

            Text(
                text = str(helpTextRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("settings-widget-guidance")
            )

            if (canPin) {
                Button(
                    onClick = {
                        val dispatched = pinWidgetToHomeScreen()
                        if (dispatched) pinRequested = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = str(Res.string.add_widget_to_home))
                }

                AnimatedVisibility(
                    visible = pinRequested,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    WidgetPinFeedback()
                }
            }
        }
    }
}

@Composable
private fun WidgetHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = str(Res.string.onboarding_widget_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ScenarioSwitcher(
    selected: WidgetPreviewScenario,
    onSelect: (WidgetPreviewScenario) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WidgetPreviewScenario.entries.forEach { scenario ->
            val labelRes = when (scenario) {
                WidgetPreviewScenario.READY -> Res.string.status_awaiting
                WidgetPreviewScenario.PRESENT -> Res.string.status_present
                WidgetPreviewScenario.LATE -> Res.string.status_late
                WidgetPreviewScenario.DAY_OFF -> Res.string.status_day_off
            }
            FilterChip(
                selected = selected == scenario,
                onClick = { onSelect(scenario) },
                label = {
                    Text(
                        text = str(labelRes),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun WidgetPreviewMockup(
    scenario: WidgetPreviewScenario,
    onFastRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daymark",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = str(Res.string.widget_preview_header),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedContent(
                targetState = scenario,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + slideInVertically { it / 3 }) togetherWith
                            (fadeOut(animationSpec = tween(180)) + slideOutVertically { -it / 3 })
                },
                label = "WidgetMockupTransition"
            ) { targetScenario ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusPill(scenario = targetScenario)

                    FastRegisterButton(
                        scenario = targetScenario,
                        onFastRegister = onFastRegister
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(scenario: WidgetPreviewScenario) {
    val (containerColor, contentColor) = when (scenario) {
        WidgetPreviewScenario.READY -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
        WidgetPreviewScenario.PRESENT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        WidgetPreviewScenario.LATE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        WidgetPreviewScenario.DAY_OFF -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

    val statusText = when (scenario) {
        WidgetPreviewScenario.READY -> str(Res.string.status_awaiting)
        WidgetPreviewScenario.PRESENT -> str(Res.string.status_present)
        WidgetPreviewScenario.LATE -> str(Res.string.status_late)
        WidgetPreviewScenario.DAY_OFF -> str(Res.string.status_day_off)
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (scenario) {
                WidgetPreviewScenario.READY -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "PulsingBeaconTransition")
                    val beaconAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(900, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "PulsingBeaconAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .alpha(beaconAlpha)
                    )
                }
                WidgetPreviewScenario.PRESENT -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                WidgetPreviewScenario.LATE -> {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                WidgetPreviewScenario.DAY_OFF -> {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = contentColor
            )
        }
    }
}

@Composable
private fun FastRegisterButton(
    scenario: WidgetPreviewScenario,
    onFastRegister: () -> Unit
) {
    when (scenario) {
        WidgetPreviewScenario.READY -> {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.96f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "FastRegisterButtonScale"
            )

            Button(
                onClick = onFastRegister,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                Text(
                    text = str(Res.string.check_attendance),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
        WidgetPreviewScenario.PRESENT -> {
            FilledTonalButton(
                onClick = {},
                enabled = false,
                colors = ButtonDefaults.filledTonalButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = str(Res.string.status_present),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                )
            }
        }
        WidgetPreviewScenario.LATE -> {
            FilledTonalButton(
                onClick = {},
                enabled = false,
                colors = ButtonDefaults.filledTonalButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.tertiary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = str(Res.string.status_late),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                )
            }
        }
        WidgetPreviewScenario.DAY_OFF -> {
            FilledTonalButton(
                onClick = {},
                enabled = false,
                colors = ButtonDefaults.filledTonalButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = str(Res.string.status_day_off),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun WidgetPinFeedback() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = str(Res.string.widget_pin_requested),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
