package dev.daymark.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.location.PlaceAddressRequest
import dev.daymark.resources.Res
import dev.daymark.resources.registered_coordinates
import dev.daymark.resources.registered_map_unavailable
import dev.daymark.resources.registered_address_loading
import dev.daymark.resources.registered_address_unavailable
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.round

private const val MAX_MAP_LATITUDE = 85.0511287798066

@Composable
internal fun RegisteredPlacePreview(
    place: RegisteredPlace,
    addressState: PlaceAddressUiState,
    placeName: String,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val address = addressState.takeIf {
        it.request == PlaceAddressRequest(place.latitude, place.longitude, language)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when {
                address?.loading == true -> str(Res.string.registered_address_loading)
                address?.address != null -> isolated(address.address)
                placeName.isNotBlank() -> isolated(placeName)
                else -> str(Res.string.registered_address_unavailable)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("registered-place-address")
        )
        Text(
            str(
                Res.string.registered_coordinates,
                isolated(previewCoordinate(place.latitude, language)),
                isolated(previewCoordinate(place.longitude, language))
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("setup-place-coordinates")
        )
        if (abs(place.latitude) <= MAX_MAP_LATITUDE) {
            RegisteredPlaceMap(place, Modifier.fillMaxWidth())
        } else Text(str(Res.string.registered_map_unavailable))
    }
}

@Composable
internal expect fun RegisteredPlaceMap(place: RegisteredPlace, modifier: Modifier)

internal fun registeredPlaceZoom(place: RegisteredPlace, shortSideDp: Float): Double {
    val metresPerDpAtZoomZero = 40_075_016.686 * cos(place.latitude * kotlin.math.PI / 180) / 512
    val targetMetresPerDp = place.radiusMetres * 2 / (shortSideDp * 0.75)
    return log2(metresPerDpAtZoomZero / targetMetresPerDp).coerceIn(0.0, 22.0)
}

internal fun previewCoordinate(coordinate: Double, language: AppLanguage): String {
    val scaled = round(coordinate * 100_000).toLong()
    val magnitude = abs(scaled)
    val sign = if (scaled < 0) "-" else ""
    val decimal = "$sign${magnitude / 100_000}.${(magnitude % 100_000).toString().padStart(5, '0')}"
    if (language == AppLanguage.EN) return decimal
    return decimal.map { character ->
        when (character) {
            in '0'..'9' -> "٠١٢٣٤٥٦٧٨٩"[character - '0']
            '.' -> '٫'
            else -> character
        }
    }.joinToString("")
}
