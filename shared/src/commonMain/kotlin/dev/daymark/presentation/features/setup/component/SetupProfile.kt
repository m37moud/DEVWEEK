package dev.daymark.presentation.features.setup.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.setup.SetupDraft
import dev.daymark.presentation.component.label
import dev.daymark.presentation.features.setup.SetupUiState
import dev.daymark.reporting.REPORT_TEXT_LIMIT
import dev.daymark.reporting.exceedsReportTextLimit
import dev.daymark.resources.Res
import dev.daymark.resources.profile_too_long
import dev.daymark.resources.setup_name
import dev.daymark.resources.setup_type
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str

@Composable
internal fun SetupProfile(state: SetupUiState, onDraftChange: (SetupDraft) -> Unit) {
    val draft = state.draft
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = draft.displayName,
            onValueChange = { onDraftChange(draft.copy(displayName = it)) },
            label = { Text(str(Res.string.setup_name)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup-name")
        )

        AnimatedVisibility(
            visible = exceedsReportTextLimit(draft.displayName),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = str(
                    Res.string.profile_too_long,
                    DisplayFormats.number(REPORT_TEXT_LIMIT, LocalAppLanguage.current)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = str(Res.string.setup_type),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrackerCategory.entries.forEach { category ->
                    FilterChip(
                        selected = category == draft.category,
                        enabled = !state.busy,
                        onClick = { onDraftChange(draft.copy(category = category)) },
                        label = { Text(str(category.label())) },
                        modifier = Modifier.heightIn(min = 40.dp)
                    )
                }
            }
        }
    }
}
