package dev.daymark.presentation.features.settings.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanState
import dev.daymark.presentation.features.settings.AttendanceSettingsUiState
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.onboarding_place
import dev.daymark.resources.plan_review
import dev.daymark.resources.radius_metres
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str

@Composable
internal fun RegisteredPlaceCard(
    plan: PlanState,
    draft: PlanChange?,
    state: AttendanceSettingsUiState,
    actions: AttendanceSettingsActions,
    editing: Boolean,
    onToggleEditing: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    SettingsEditorCard(
        header = SettingsEditorHeader(
            title = Res.string.onboarding_place,
            editTag = "settings-place-edit",
            closeLabel = Res.string.cancel,
            editing = editing,
            enabled = !state.busy && onToggleEditing != null
        ),
        onToggle = onToggleEditing ?: {},
        modifier = modifier
    ) {
        if (editing && draft != null) {
            RegisteredPlaceEditor(
                state = state,
                draft = draft,
                actions = actions
            )
        } else {
            RegisteredPlaceSummary(plan = plan)
        }
    }
}

@Composable
private fun RegisteredPlaceSummary(
    plan: PlanState,
    modifier: Modifier = Modifier
) {
    val saved = plan.pending ?: plan.current
    val language = LocalAppLanguage.current
    val radiusText = isolated(value = DisplayFormats.number(value = saved.place.radiusMetres, language = language))
    val radiusLabel = str(resource = Res.string.radius_metres, radiusText)
    val placeTitle = if (plan.placeName.isBlank()) {
        str(resource = Res.string.onboarding_place)
    } else {
        isolated(value = plan.placeName)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
    ) {
        Row(
            modifier = Modifier.weight(weight = 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size = 18.dp)
            )
            Text(
                text = placeTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = radiusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun RegisteredPlaceEditor(
    state: AttendanceSettingsUiState,
    draft: PlanChange,
    actions: AttendanceSettingsActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 16.dp)
    ) {
        PlanPlaceEditor(
            state = PlanPlaceUiState(
                place = draft.place,
                address = state.placeAddress,
                placeName = state.plan?.placeName.orEmpty(),
                busy = state.busy,
                locationIssue = state.locationIssue
            ),
            actions = PlanPlaceActions(
                changeRadius = { radius ->
                    actions.edit { it.copy(place = it.place.copy(radiusMetres = radius)) }
                },
                registerCurrentPlace = actions.registerCurrentPlace,
                openLocationSettings = actions.openLocationSettings
            )
        )

        AttendanceEditorReviewFooter(
            state = state,
            draft = draft,
            actions = actions,
            reviewTestTag = "settings-place-review",
            reviewButtonText = str(resource = Res.string.plan_review)
        )
    }
}
