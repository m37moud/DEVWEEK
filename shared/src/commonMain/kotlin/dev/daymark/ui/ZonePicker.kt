package dev.daymark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.resources.*
import dev.daymark.settings.*
import kotlinx.datetime.TimeZone

@Composable internal fun ZonePicker(current: String,onSelect: (String) -> Unit,onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val zones = remember(query,current) { TimeZone.availableZoneIds.filter { it.contains(query,ignoreCase = true) }
        .sortedWith(compareBy<String> { it != current }.thenBy { it }) }
    AlertDialog(onDismissRequest = onDismiss,title = { Text(str(Res.string.choose_zone)) },text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = query,onValueChange = { query = it },singleLine = true,label = { Text(str(Res.string.search_zone)) },modifier = Modifier.testTag("zone-search"))
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(zones,key = { it }) { zone ->
                    TextButton(onClick = { onSelect(zone) },modifier = Modifier.fillMaxWidth().testTag("zone-choice-$zone")) { Text(isolated(zone)) }
                }
            }
        }
    },confirmButton = {},dismissButton = { TextButton(onClick = onDismiss) { Text(str(Res.string.cancel)) } })
}
