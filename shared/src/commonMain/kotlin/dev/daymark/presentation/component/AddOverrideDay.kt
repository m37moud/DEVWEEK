package dev.daymark.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import dev.daymark.resources.Res
import dev.daymark.resources.add_day_override
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek

@Composable
internal fun AddOverrideDay(
    days: List<DayOfWeek>,
    busy: Boolean,
    onSelect: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = !busy && days.isNotEmpty(),
            elevation = ButtonDefaults.elevatedButtonElevation(10.dp),
            shape = CircleShape
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Plus,
                    contentDescription = str(Res.string.add_day_override),
                    modifier = Modifier.size(16.dp)
                )
//                Text(text = str(Res.string.add_day_override))
            }
        }
        DropdownMenu(expanded = expanded && !busy, onDismissRequest = { expanded = false }) {
            for (day in days) {
                DropdownMenuItem(
                    text = { Text(str(day.label())) },
                    onClick = {
                        expanded = false
                        onSelect(day)
                    },
                )
            }
        }
    }
}
