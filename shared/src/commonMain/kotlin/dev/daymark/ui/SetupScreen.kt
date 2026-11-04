package dev.daymark.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.daymark.app.SetupDraft
import dev.daymark.attendance.RegisteredPlace
import dev.daymark.attendance.TrackerCategory
import dev.daymark.resources.Res
import dev.daymark.resources.balance_help
import dev.daymark.resources.checking_location
import dev.daymark.resources.course
import dev.daymark.resources.friday
import dev.daymark.resources.invalid_setup
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.location_denied
import dev.daymark.resources.location_disabled
import dev.daymark.resources.location_inaccurate
import dev.daymark.resources.location_rationale
import dev.daymark.resources.location_stale
import dev.daymark.resources.location_timeout
import dev.daymark.resources.location_unavailable
import dev.daymark.resources.monday
import dev.daymark.resources.open_settings
import dev.daymark.resources.opening_balance
import dev.daymark.resources.place_ready
import dev.daymark.resources.profile_too_long
import dev.daymark.resources.radius
import dev.daymark.resources.radius_metres
import dev.daymark.resources.register_place
import dev.daymark.resources.saturday
import dev.daymark.resources.school
import dev.daymark.resources.setup_name
import dev.daymark.resources.setup_place_name
import dev.daymark.resources.setup_type
import dev.daymark.resources.sunday
import dev.daymark.resources.thursday
import dev.daymark.resources.tuesday
import dev.daymark.resources.wednesday
import dev.daymark.resources.work
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt

internal data class SetupActions(
    val updateDraft: (SetupDraft) -> Unit,
    val registerPlace: () -> Unit,
    val openSettings: () -> Unit
)

@Composable
internal fun SetupProfile(state: SetupUiState, onDraftChange: (SetupDraft) -> Unit) {
    val draft = state.draft
    OutlinedTextField(
        draft.displayName, { onDraftChange(draft.copy(displayName = it)) },
        label = { Text(str(Res.string.setup_name)) }, singleLine = true,
        enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("setup-name")
    )
    if (dev.daymark.reporting.exceedsReportTextLimit(draft.displayName)) {
        Text(
            str(
                Res.string.profile_too_long,
                DisplayFormats.number(
                    dev.daymark.reporting.REPORT_TEXT_LIMIT,
                    LocalAppLanguage.current
                )
            ),
            color = MaterialTheme.colorScheme.error
        )
    }
    Text(str(Res.string.setup_type), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrackerCategory.entries.forEach { category ->
            FilterChip(
                selected = category == draft.category, enabled = !state.busy,
                onClick = { onDraftChange(draft.copy(category = category)) },
                label = { Text(str(category.label())) })
        }
    }
}

@Composable
internal fun SetupPlace(state: SetupUiState, actions: SetupActions) {
    val draft = state.draft
    OutlinedTextField(
        draft.placeName, { actions.updateDraft(draft.copy(placeName = it)) },
        label = { Text(str(Res.string.setup_place_name)) }, singleLine = true,
        enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("setup-place-name")
    )
    Text(
        str(Res.string.location_rationale),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick = actions.registerPlace, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().testTag("setup-register-place")
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = str(
                    if (state.busy)
                        Res.string.checking_location
                    else
                        Res.string.register_place
                )
            )
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    state.issue?.takeIf { it.isLocationIssue() }?.let { issue ->
        SetupError(issue)
        if (issue == SetupIssue.LOCATION_DENIED || issue == SetupIssue.LOCATION_DISABLED) {
            TextButton(onClick = actions.openSettings, enabled = !state.busy) {
                Text(str(Res.string.open_settings))
            }
        }
    }
    draft.place?.let { place ->
        PlaceRadius(
            place,
            state.busy
        ) { updated -> actions.updateDraft(draft.copy(place = updated)) }
        RegisteredPlacePreview(place, Modifier.fillMaxWidth())
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
    Text(
        str(Res.string.place_ready, metres), color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("setup-place-ready")
    )
    Text(label, style = MaterialTheme.typography.titleSmall)
    Slider(
        place.radiusMetres.toFloat(), { onPlaceChange(place.copy(radiusMetres = it.roundToInt())) },
        enabled = !busy, valueRange = 100f..500f, steps = 7,
        modifier = Modifier.testTag("setup-radius").semantics {
            contentDescription = label
            stateDescription = radiusDescription
        }
    )
}

@Composable
internal fun OpeningBalance(state: SetupUiState, onDraftChange: (SetupDraft) -> Unit) {
    OutlinedTextField(
        state.draft.openingBalanceText,
        { onDraftChange(state.draft.copy(openingBalanceText = it)) },
        label = { Text(str(Res.string.opening_balance)) },
        singleLine = true,
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        str(Res.string.balance_help), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun SetupError(issue: SetupIssue) {
    val errorPosition = remember { BringIntoViewRequester() }
    LaunchedEffect(issue) {
        withFrameNanos { }
        errorPosition.bringIntoView()
    }
    Text(
        str(issue.label()), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("setup-error")
            .semantics { liveRegion = LiveRegionMode.Assertive }
            .bringIntoViewRequester(errorPosition)
    )
}

internal fun TrackerCategory.label(): StringResource = when (this) {
    TrackerCategory.WORK -> Res.string.work; TrackerCategory.SCHOOL -> Res.string.school; TrackerCategory.COURSE -> Res.string.course
}

internal fun DayOfWeek.label(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.monday; DayOfWeek.TUESDAY -> Res.string.tuesday; DayOfWeek.WEDNESDAY -> Res.string.wednesday
    DayOfWeek.THURSDAY -> Res.string.thursday; DayOfWeek.FRIDAY -> Res.string.friday; DayOfWeek.SATURDAY -> Res.string.saturday; DayOfWeek.SUNDAY -> Res.string.sunday
}

internal fun SetupIssue.label(): StringResource = when (this) {
    SetupIssue.INVALID_INPUT -> Res.string.invalid_setup; SetupIssue.LOCATION_DENIED -> Res.string.location_denied
    SetupIssue.LOCATION_DISABLED -> Res.string.location_disabled; SetupIssue.LOCATION_TIMEOUT -> Res.string.location_timeout
    SetupIssue.LOCATION_UNAVAILABLE -> Res.string.location_unavailable; SetupIssue.LOCATION_STALE -> Res.string.location_stale
    SetupIssue.LOCATION_INACCURATE -> Res.string.location_inaccurate; SetupIssue.LOCAL_SAVE_FAILED -> Res.string.local_save_failed
}

internal fun localTimeLabel(time: LocalTime, language: AppLanguage): String {
    val zero = DisplayFormats.number(0, language).single()
    return isolated(
        DisplayFormats.number(time.hour, language).padStart(2, zero) + ":" + DisplayFormats.number(
            time.minute,
            language
        ).padStart(2, zero)
    )
}

internal fun SetupIssue.isLocationIssue() =
    this != SetupIssue.INVALID_INPUT && this != SetupIssue.LOCAL_SAVE_FAILED
