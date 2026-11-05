package dev.daymark.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.resources.Res
import dev.daymark.resources.registered_map_contributors
import dev.daymark.resources.registered_map_description
import dev.daymark.resources.registered_map_loading
import dev.daymark.resources.registered_map_privacy
import dev.daymark.resources.registered_map_provider
import dev.daymark.resources.registered_map_retry
import dev.daymark.resources.registered_map_unavailable
import dev.daymark.settings.str
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.logging.MapLogging
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MapUiOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.RenderStats
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.createMapRuntime
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DelicateMaplibreComposeApi
import org.maplibre.spatialk.geojson.Position

@Composable
internal actual fun RegisteredPlaceMap(place: RegisteredPlace, modifier: Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var attempt by remember(place.latitude, place.longitude, dark) { mutableIntStateOf(0) }
    var failed by remember(place.latitude, place.longitude, dark, attempt) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (failed) {
            Text(str(Res.string.registered_map_unavailable), Modifier.testTag("setup-map-error"))
            TextButton(onClick = { attempt++ }, modifier = Modifier.testTag("setup-map-retry")) {
                Text(str(Res.string.registered_map_retry))
            }
        } else key(place.latitude, place.longitude, dark, attempt) {
            PlaceMapContent(place, if (dark) "dark" else "liberty") { failed = true }
        }
        MapProviderCredit()
    }
}

@Composable
private fun PlaceMapContent(place: RegisteredPlace, style: String, onFailure: () -> Unit) {
    val state = rememberPlaceMapState(style)
    BoxWithConstraints(
        Modifier.height(240.dp).clip(MaterialTheme.shapes.medium).testTag("setup-place-map")
    ) {
        val camera = CameraPosition(
            center = Position(place.longitude, place.latitude),
            zoom = registeredPlaceZoom(place, min(maxWidth.value, 240f))
        )
        LaunchedEffect(state, camera) { state.setCameraPosition(camera) }
        MaplibreMap(
            modifier = Modifier.fillMaxSize(), state = state,
            interactions = MapInteractions.None, uiOptions = MapUiOptions.None, overlay = {}
        )
        PlaceMapMarker(state, place)
        PlaceMapLoading(state, onFailure)
    }
}

@OptIn(DelicateMaplibreComposeApi::class)
@Composable
private fun rememberPlaceMapState(style: String): MapState {
    val runtime = remember {
        // Engine diagnostics can include coordinates and map-resource URLs.
        MapLogging.logger = null
        createMapRuntime()
    }
    DisposableEffect(runtime) { onDispose { runtime.close() } }
    return rememberMapState(
        runtime,
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/$style")
    )
}

@Composable
private fun PlaceMapMarker(state: MapState, place: RegisteredPlace) {
    val primary = MaterialTheme.colorScheme.primary
    val description = str(Res.string.registered_map_description)
    Box(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = description }) {
        val metresPerDp = state.viewport?.metersPerDpAtCenter
        Canvas(Modifier.fillMaxSize()) {
            if (metresPerDp != null && metresPerDp > 0) {
                val radiusPixels = (place.radiusMetres / metresPerDp).dp.toPx()
                drawCircle(primary.copy(alpha = 0.18f), radiusPixels)
                drawCircle(primary, radiusPixels, style = Stroke(2.dp.toPx()))
            }
        }
        Icon(
            Icons.Default.LocationOn, contentDescription = null, tint = primary,
            modifier = Modifier.align(Alignment.Center).offset(y = (-16).dp).size(32.dp)
        )
    }
}

@Composable
private fun PlaceMapLoading(state: MapState, onFailure: () -> Unit) {
    var loaded by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) {
        val rendered = withTimeoutOrNull(15.seconds) {
            state.events.first { event ->
                state.style.loadState == StyleLoadState.Ready &&
                        (event == MapEvent.Idle || event is MapEvent.FrameRendered && event.stats?.mode == RenderStats.Mode.Full)
            }
        }
        if (rendered == null) onFailure() else loaded = true
    }
    LaunchedEffect(state.style.loadState) {
        if (state.style.loadState is StyleLoadState.Failed) onFailure()
    }
    if (!loaded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator()
        Text(str(Res.string.registered_map_loading))
    }
}

@Composable
private fun MapProviderCredit() {
    Text(str(Res.string.registered_map_provider), style = MaterialTheme.typography.bodySmall)
    val contributors = str(Res.string.registered_map_contributors)
    val privacy = str(Res.string.registered_map_privacy)
    Text(buildAnnotatedString {
        withLink(LinkAnnotation.Url("https://openmaptiles.org/")) { append("© OpenMapTiles") }
        append(" · ")
        withLink(LinkAnnotation.Url("https://www.openstreetmap.org/copyright")) {
            append(
                contributors
            )
        }
        append(" · ")
        withLink(LinkAnnotation.Url("https://openfreemap.org/privacy/")) { append(privacy) }
    }, style = MaterialTheme.typography.bodySmall)
}
