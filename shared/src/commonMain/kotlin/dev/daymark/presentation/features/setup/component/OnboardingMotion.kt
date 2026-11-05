package dev.daymark.presentation.features.setup.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.daymark.app.SetupStep
import dev.daymark.app.reducedMotionEnabled
import dev.daymark.presentation.component.label
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
internal fun OnboardingStep(step: SetupStep, content: @Composable ColumnScope.() -> Unit) {
    val reduced = rememberOnboardingReducedMotion()
    var previous by remember { mutableStateOf(step) }
    val direction = remember(step) { if (step.ordinal < previous.ordinal) -1 else 1 }
    val rtl = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val distance = with(LocalDensity.current) { 24.dp.toPx() } * direction * rtl
    val entrance = remember(step, reduced) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(step, reduced) {
        previous = step
        entrance.animateTo(1f, tween(if (reduced) 0 else 250, easing = FastOutSlowInEasing))
    }
    // Only the current step is composed; outgoing controls cannot receive focus or taps.
    key(step) {
        Column(Modifier.fillMaxWidth().graphicsLayer {
            alpha = entrance.value
            translationX = distance * (1f - entrance.value)
        }, verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun OnboardingProgress(step: SetupStep) {
    if (step == SetupStep.WELCOME) return
    val reduced = rememberOnboardingReducedMotion()
    val progress by animateFloatAsState(
        step.ordinal / 4f,
        tween(if (reduced) 0 else 200), label = "setup progress"
    )
    val language = LocalAppLanguage.current
    val label = str(
        Res.string.onboarding_step,
        DisplayFormats.number(step.ordinal, language), DisplayFormats.number(4, language)
    )
    Text(label, style = MaterialTheme.typography.labelLarge)
    LinearProgressIndicator(
        progress = { progress }, modifier = Modifier.fillMaxWidth()
            .clearAndSetSemantics { contentDescription = label })
}
