package dev.daymark.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.presentation.component.label
import dev.daymark.presentation.features.setup.title
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.choose_zone
import dev.daymark.resources.search_zone
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import kotlinx.datetime.TimeZone

@Composable
internal fun ZonePicker(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val zones = remember(query, current) {
        TimeZone.availableZoneIds.filter { it.contains(query, ignoreCase = true) }
            .sortedWith(compareBy<String> { it != current }.thenBy { it })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(Res.string.choose_zone)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(str(Res.string.search_zone)) },
                    modifier = Modifier.testTag("zone-search")
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(zones, key = { it }) { zone ->
                        TextButton(
                            onClick = { onSelect(zone) },
                            modifier = Modifier.fillMaxWidth().testTag("zone-choice-$zone")
                        ) { Text(isolated(zone)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(Res.string.cancel)) } })
}
