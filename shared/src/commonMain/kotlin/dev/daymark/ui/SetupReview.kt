package dev.daymark.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.app.HostPlatform
import dev.daymark.app.SetupDraft
import dev.daymark.app.SetupStep
import dev.daymark.attendance.TrackerCategory
import dev.daymark.resources.*
import dev.daymark.settings.*

@Composable
internal fun SetupReview(state: SetupUiState, platform: HostPlatform, onEdit: (SetupStep) -> Unit) {
    SetupReviewSection(str(Res.string.onboarding_profile), onEdit = { onEdit(SetupStep.PROFILE) }) {
        Text(isolated(state.draft.displayName))
        state.draft.category?.let { Text(str(it.label())) }
    }
    SetupReviewSection(str(Res.string.onboarding_place), onEdit = { onEdit(SetupStep.PLACE) }) {
        Text(isolated(state.draft.placeName))
        state.draft.place?.let {
            Text(
                str(
                    Res.string.place_ready,
                    isolated(DisplayFormats.number(it.radiusMetres, LocalAppLanguage.current))
                )
            )
        }
    }
    SetupReviewSection(str(Res.string.schedule), onEdit = { onEdit(SetupStep.SCHEDULE) }) {
        ScheduleSummary(state.draft)
    }
    Text(str(if (platform == HostPlatform.ANDROID) Res.string.automation_help else Res.string.ios_manual_help))
    if (platform == HostPlatform.IOS) Text(str(Res.string.reminder_horizon))
    Text(str(Res.string.google_optional))
    Text(str(Res.string.onboarding_extras_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SetupReviewSection(
    title: String,
    onEdit: () -> Unit,
    content: @Composable () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
            TextButton(onClick = onEdit) { Text(str(Res.string.onboarding_edit_section, title)) }
        }
    }
}

@Composable
private fun ScheduleSummary(draft: SetupDraft) {
    val language = LocalAppLanguage.current
    draft.schedule?.let { schedule ->
        schedule.days.sortedBy { it.ordinal }.forEach { day ->
            Text(str(day.label()) + " · " + localTimeLabel(schedule.overrides[day] ?: schedule.arrival, language))
        }
        Text(
            str(Res.string.grace) + " · " + plural(
                Res.plurals.grace_minutes,
                schedule.graceMinutes, isolated(DisplayFormats.number(schedule.graceMinutes, language))
            )
        )
        Text(str(Res.string.attendance_zone, isolated(schedule.zoneId)))
    }
    if (draft.category == TrackerCategory.WORK) Text(str(Res.string.opening_balance) + " · " + isolated(draft.openingBalanceText))
}

@Composable
internal fun WidgetSetupGuidance() {
    Text(str(Res.string.onboarding_widget_title), style = MaterialTheme.typography.titleLarge)
    Text(str(Res.string.onboarding_widget_help), modifier = Modifier.testTag("settings-widget-guidance"))
}
