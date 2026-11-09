package dev.daymark.presentation.features.setup.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.daymark.app.SetupStep
import dev.daymark.app.reducedMotionEnabled
import dev.daymark.resources.Res
import dev.daymark.resources.onboarding_step
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str

@Composable
internal fun rememberOnboardingReducedMotion(): Boolean {
    var reduced by remember { mutableStateOf(reducedMotionEnabled()) }
    LifecycleStartEffect(Unit) {
        reduced = reducedMotionEnabled()
        onStopOrDispose { }
    }
    return reduced
}

@Composable
internal fun OnboardingStep(
    step: SetupStep,
    content: @Composable ColumnScope.(SetupStep) -> Unit
) {
    val reduced = rememberOnboardingReducedMotion()
    val entranceSpec = tween<Float>(250, easing = FastOutSlowInEasing)
    // Remove outgoing controls immediately so they cannot receive taps or accessibility focus.
    key(step) {
        val entrance = remember(reduced) { Animatable(if (reduced) 1f else 0f) }
        LaunchedEffect(reduced) {
            entrance.animateTo(1f, if (reduced) snap() else entranceSpec)
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer {
                    alpha = entrance.value
                    scaleX = 0.96f + 0.04f * entrance.value
                    scaleY = scaleX
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content(step)
        }
    }
}

@Composable
internal fun OnboardingProgress(step: SetupStep) {
    if (step == SetupStep.WELCOME) return
    val reduced = rememberOnboardingReducedMotion()
    val language = LocalAppLanguage.current
    val totalSteps = SetupStep.entries.size - 1
    val currentStepNumber = step.ordinal
    val stepLabel = str(
        Res.string.onboarding_step,
        DisplayFormats.number(currentStepNumber, language),
        DisplayFormats.number(totalSteps, language)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = stepLabel
                stateDescription = stepLabel
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (index in 1..totalSteps) {
                val isCompletedOrCurrent = currentStepNumber >= index
                val segmentFraction by animateFloatAsState(
                    targetValue = if (isCompletedOrCurrent) 1f else 0f,
                    animationSpec = tween(if (reduced) 0 else 250, easing = FastOutSlowInEasing),
                    label = "segment_fill_$index"
                )
                val segmentColor by animateColorAsState(
                    targetValue = if (isCompletedOrCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    },
                    animationSpec = tween(if (reduced) 0 else 250),
                    label = "segment_color_$index"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = segmentFraction)
                            .fillMaxHeight()
                            .background(segmentColor)
                    )
                }
            }
        }
    }
}
