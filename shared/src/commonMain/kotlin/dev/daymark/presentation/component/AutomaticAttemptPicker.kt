package dev.daymark.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.daymark.attendance.automaticAttemptLimits
import dev.daymark.resources.Res
import dev.daymark.resources.automatic_attempt_help
import dev.daymark.resources.automatic_attempt_limit
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str

@Composable
internal fun AutomaticAttemptPicker(
    limit: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = str(Res.string.automatic_attempt_limit),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (count in automaticAttemptLimits) {
                FilterChip(
                    selected = limit == count,
                    onClick = { onSelect(count) },
                    enabled = enabled,
                    label = { Text(text = DisplayFormats.number(count, LocalAppLanguage.current)) }
                )
            }
        }
        Text(
            text = str(Res.string.automatic_attempt_help),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
