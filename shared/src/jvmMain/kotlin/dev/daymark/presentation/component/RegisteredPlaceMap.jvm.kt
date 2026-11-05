package dev.daymark.presentation.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.resources.Res
import dev.daymark.resources.registered_map_unavailable
import dev.daymark.settings.str

@Composable
internal actual fun RegisteredPlaceMap(place: RegisteredPlace, modifier: Modifier) {
    Text(str(Res.string.registered_map_unavailable), modifier = modifier)
}
