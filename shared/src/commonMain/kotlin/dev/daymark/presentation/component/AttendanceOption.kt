package dev.daymark.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

internal data class AttendanceOptionState(
    val label: String,
    val selected: Boolean,
    val description: String? = null,
    val enabled: Boolean = true
)

@Composable
internal fun AttendanceOption(
    state: AttendanceOptionState,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
            value = state.selected,
            enabled = state.enabled,
            role = Role.Switch,
            onValueChange = onChange
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = state.label, style = MaterialTheme.typography.titleSmall)
            state.description?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        }
        Switch(checked = state.selected, onCheckedChange = null, enabled = state.enabled)
    }
}
