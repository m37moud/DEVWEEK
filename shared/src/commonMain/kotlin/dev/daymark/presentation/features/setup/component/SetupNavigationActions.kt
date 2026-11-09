package dev.daymark.presentation.features.setup.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.daymark.app.SetupStep
import dev.daymark.presentation.features.setup.OnboardingActions
import dev.daymark.presentation.features.setup.SetupUiState
import dev.daymark.resources.Res
import dev.daymark.resources.onboarding_back
import dev.daymark.resources.onboarding_get_started
import dev.daymark.resources.onboarding_next
import dev.daymark.settings.str

// shortcut: M3 1.9 motion specs are internal; use theme specs when they become public.
private val ActionSpatialSpec = spring<IntSize>(dampingRatio = 0.9f, stiffness = 700f)
private val ActionEffectsSpec = spring<Float>(dampingRatio = 1f, stiffness = 1600f)

@Composable
internal fun SetupNavigationActions(state: SetupUiState, actions: OnboardingActions) {
    val reduced = rememberOnboardingReducedMotion()
    val spatialSpec = if (reduced) snap() else ActionSpatialSpec
    val effectsSpec = if (reduced) snap() else ActionEffectsSpec
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SetupBackTransition(state, actions.back, spatialSpec, effectsSpec)
        SetupAdvanceAction(state, actions.advance, effectsSpec, Modifier.weight(1f))
    }
}

@Composable
private fun SetupBackTransition(
    state: SetupUiState,
    onBack: () -> Unit,
    spatialSpec: FiniteAnimationSpec<IntSize>,
    effectsSpec: FiniteAnimationSpec<Float>
) {
    val showBack = state.draft.step != SetupStep.WELCOME
    AnimatedVisibility(
        visible = showBack,
        enter = expandHorizontally(spatialSpec, Alignment.Start) + fadeIn(effectsSpec),
        exit = shrinkHorizontally(spatialSpec, Alignment.Start) + fadeOut(effectsSpec),
        modifier = if (showBack) Modifier else Modifier.clearAndSetSemantics { }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetupBackAction(state, onBack)
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun SetupBackAction(state: SetupUiState, onBack: () -> Unit) {
    OutlinedButton(
        onClick = onBack,
        enabled = !state.interactionBusy && state.draft.step != SetupStep.WELCOME,
        modifier = Modifier.heightIn(min = 48.dp).testTag("setup-back"),
        contentPadding = PaddingValues(horizontal = 24.dp),
        elevation = ButtonDefaults.elevatedButtonElevation(10.dp)
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = str(Res.string.onboarding_back),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).testTag("setup-back-icon")
            )
        }
    }
}

@Composable
private fun SetupAdvanceAction(
    state: SetupUiState,
    onAdvance: () -> Unit,
    effectsSpec: FiniteAnimationSpec<Float>,
    modifier: Modifier
) {
    Button(
        onClick = onAdvance,
        enabled = !state.interactionBusy,
        modifier = modifier.heightIn(min = 48.dp).testTag("setup-next")
    ) {
        SetupAdvanceContent(state.draft.step, effectsSpec)
    }
}

@Composable
private fun SetupAdvanceContent(step: SetupStep, effectsSpec: FiniteAnimationSpec<Float>) {
    val welcome = step == SetupStep.WELCOME
    Crossfade(
        targetState = welcome,
        animationSpec = effectsSpec,
        label = "setup-primary-label"
    ) { showingWelcome ->
        SetupAdvanceLabel(
            str(if (showingWelcome) Res.string.onboarding_get_started else Res.string.onboarding_next),
            if (showingWelcome == welcome) Modifier else Modifier.clearAndSetSemantics { }
        )
    }
}

@Composable
private fun SetupAdvanceLabel(label: String, modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}
