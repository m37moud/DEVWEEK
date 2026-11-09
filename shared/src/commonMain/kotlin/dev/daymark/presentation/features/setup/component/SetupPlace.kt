package dev.daymark.presentation.features.setup.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.setup.SetupDraft
import dev.daymark.presentation.component.RegisteredPlacePreview
import dev.daymark.presentation.component.isLocationIssue
import dev.daymark.presentation.features.setup.SetupIssue
import dev.daymark.presentation.features.setup.SetupUiState
import dev.daymark.resources.Res
import dev.daymark.resources.checking_location
import dev.daymark.resources.location_rationale
import dev.daymark.resources.open_settings
import dev.daymark.resources.place_ready
import dev.daymark.resources.radius
import dev.daymark.resources.radius_metres
import dev.daymark.resources.register_place
import dev.daymark.resources.setup_place_name
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import kotlin.math.roundToInt

internal data class SetupActions(
    val updateDraft: (SetupDraft) -> Unit,
    val registerPlace: () -> Unit,
    val openSettings: () -> Unit,
    val setLanguage: (LanguagePreference) -> Unit,
    val setTheme: (ThemePreference) -> Unit
)

@Composable
internal fun SetupPlace(state: SetupUiState, actions: SetupActions) {
    val draft = state.draft
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = draft.placeName,
            onValueChange = { actions.updateDraft(draft.copy(placeName = it)) },
            label = { Text(str(Res.string.setup_place_name)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup-place-name")
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = str(Res.string.location_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = actions.registerPlace,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("setup-register-place")
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = str(
                        if (state.busy) Res.string.checking_location
                        else Res.string.register_place
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        state.issue?.takeIf { it.isLocationIssue() }?.let { issue ->
            SetupError(issue)
            if (issue == SetupIssue.LOCATION_DENIED || issue == SetupIssue.LOCATION_DISABLED) {
                TextButton(
                    onClick = actions.openSettings,
                    enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(str(Res.string.open_settings))
                }
            }
        }

        draft.place?.let { place ->
            PlaceRadius(
                place = place,
                busy = state.busy
            ) { updated -> actions.updateDraft(draft.copy(place = updated)) }

            RegisteredPlacePreview(
                place = place,
                addressState = state.placeAddress,
                placeName = draft.placeName,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PlaceRadius(
    place: RegisteredPlace,
    busy: Boolean,
    onPlaceChange: (RegisteredPlace) -> Unit
) {
    val metres = isolated(DisplayFormats.number(place.radiusMetres, LocalAppLanguage.current))
    val label = str(Res.string.radius)
    val radiusDescription = str(Res.string.radius_metres, metres)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = str(Res.string.place_ready, metres),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("setup-place-ready")
                )
                Text(
                    text = radiusDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = place.radiusMetres.toFloat(),
                onValueChange = { onPlaceChange(place.copy(radiusMetres = it.roundToInt())) },
                enabled = !busy,
                valueRange = 100f..500f,
                steps = 7,
                modifier = Modifier
                    .testTag("setup-radius")
                    .semantics {
                        contentDescription = label
                        stateDescription = radiusDescription
                    }
            )
        }
    }
}
