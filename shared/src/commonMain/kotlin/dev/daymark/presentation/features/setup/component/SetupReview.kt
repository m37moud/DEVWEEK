package dev.daymark.presentation.features.setup.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.daymark.app.HostPlatform
import dev.daymark.app.SetupStep
import dev.daymark.app.openingBalanceDays
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.setup.SetupDraft
import dev.daymark.presentation.component.label
import dev.daymark.presentation.component.localTimeLabel
import dev.daymark.presentation.features.setup.SetupUiState
import dev.daymark.resources.Res
import dev.daymark.resources.attendance_zone
import dev.daymark.resources.automation_help
import dev.daymark.resources.departure_cutoff
import dev.daymark.resources.departure_none
import dev.daymark.resources.desktop_manual_help
import dev.daymark.resources.google_optional
import dev.daymark.resources.grace
import dev.daymark.resources.grace_minutes
import dev.daymark.resources.ios_manual_help
import dev.daymark.resources.onboarding_edit_section
import dev.daymark.resources.onboarding_extras_help
import dev.daymark.resources.onboarding_place
import dev.daymark.resources.onboarding_profile
import dev.daymark.resources.opening_balance
import dev.daymark.resources.place_ready
import dev.daymark.resources.reminder_horizon
import dev.daymark.resources.schedule
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.plural
import dev.daymark.settings.str

@Composable
internal fun SetupReview(state: SetupUiState, platform: HostPlatform, onEdit: (SetupStep) -> Unit) {
    val editEnabled = !state.interactionBusy && !state.completed
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SetupReviewSection(
            title = str(Res.string.onboarding_profile),
            editEnabled = editEnabled,
            onEdit = { onEdit(SetupStep.PROFILE) }
        ) {
            Text(
                text = isolated(state.draft.displayName),
                style = MaterialTheme.typography.bodyLarge
            )
            state.draft.category?.let {
                Text(
                    text = str(it.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SetupReviewSection(
            title = str(Res.string.onboarding_place),
            editEnabled = editEnabled,
            onEdit = { onEdit(SetupStep.PLACE) }
        ) {
            Text(
                text = isolated(state.draft.placeName),
                style = MaterialTheme.typography.bodyLarge
            )
            state.draft.place?.let {
                Text(
                    text = str(
                        Res.string.place_ready,
                        isolated(DisplayFormats.number(it.radiusMetres, LocalAppLanguage.current))
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        SetupReviewSection(
            title = str(Res.string.schedule),
            editEnabled = editEnabled,
            onEdit = { onEdit(SetupStep.SCHEDULE) }
        ) {
            ScheduleSummary(state.draft)
        }

        SetupReviewHint(platform)
    }
}

@Composable
private fun SetupReviewHint(platform: HostPlatform) {
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = str(
                        when (platform) {
                            HostPlatform.ANDROID -> Res.string.automation_help
                            HostPlatform.IOS -> Res.string.ios_manual_help
                            HostPlatform.TEST -> Res.string.desktop_manual_help
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (platform == HostPlatform.IOS) {
                Text(
                    text = str(Res.string.reminder_horizon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = str(Res.string.google_optional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (platform != HostPlatform.TEST) {
                Text(
                    text = str(Res.string.onboarding_extras_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SetupReviewSection(
    title: String,
    editEnabled: Boolean,
    onEdit: () -> Unit,
    content: @Composable () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(
                    onClick = onEdit,
                    enabled = editEnabled,
//                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = str(Res.string.onboarding_edit_section, title),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun ScheduleSummary(
    draft: SetupDraft,
    appLanguage: AppLanguage = LocalAppLanguage.current
) {
    draft.schedule?.let { schedule ->
        schedule.days.sortedBy { it.ordinal }.forEach { day ->
            Text(
                text = str(day.label()) + " · " + localTimeLabel(
                    schedule.overrides[day] ?: schedule.arrival, appLanguage
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            val departure = if (schedule.departureOverrides.containsKey(day))
                schedule.departureOverrides[day] else schedule.departure
            Text(
                text = str(Res.string.departure_cutoff) + " · " +
                        (departure?.let { localTimeLabel(it, appLanguage) }
                            ?: str(Res.string.departure_none)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = str(Res.string.grace) + " · " + plural(
                Res.plurals.grace_minutes,
                schedule.graceMinutes,
                isolated(DisplayFormats.number(schedule.graceMinutes, appLanguage))
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = str(Res.string.attendance_zone, isolated(schedule.zoneId)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (draft.category == TrackerCategory.WORK) {
        val openingBalance = draft.openingBalanceDays() ?: return
        Text(
            text = str(Res.string.opening_balance) + " · " +
                    isolated(DisplayFormats.number(openingBalance, appLanguage)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
