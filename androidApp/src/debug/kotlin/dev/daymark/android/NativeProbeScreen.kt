package dev.daymark.android

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import dev.daymark.app.App
import dev.daymark.location.AndroidLocationGateway
import dev.daymark.ui.LocationProbeContent
import dev.daymark.reporting.GoogleConnectivityProbe
import kotlinx.coroutines.launch

@Composable
internal fun DaymarkHost(permissions: AndroidLocationPermissions, googleAuth: AndroidGoogleAuth) {
    val context = LocalContext.current
    if ((context as? Activity)?.intent?.getBooleanExtra("daymarkDeveloperProbe", false) == true) {
        NativeDeveloperProbe(permissions, googleAuth)
    } else App((context.applicationContext as DaymarkApplication).graph.sharedGraph(permissions))
}

@Composable
private fun NativeDeveloperProbe(permissions: AndroidLocationPermissions, googleAuth: AndroidGoogleAuth) {
    val context = LocalContext.current.applicationContext
    val gateway = remember(context) { AndroidLocationGateway(context) }
    var backgroundResult by remember { mutableStateOf("Background probe not scheduled") }
    var googleResult by remember { mutableStateOf("Developer report is retained in Google Drive; local OAuth registration is required.") }
    var googleBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LocationProbeContent(gateway, permissions, backgroundControls = true) {
        Button(onClick = {
            scope.launch {
                val graph = (context as DaymarkApplication).graph
                backgroundResult = if (graph.recoverSafely()) "Production attendance schedule refreshed; configure a tracker to enable it"
                    else "Could not save or refresh the local schedule"
            }
        }) { Text("Refresh production attendance schedule") }
        Text(backgroundResult)
        Button(enabled = !googleBusy, onClick = {
            googleBusy = true
            scope.launch {
                try { googleResult = "Google developer probe: ${GoogleConnectivityProbe(googleAuth).run().name}" }
                finally { googleBusy = false }
            }
        }) { Text("Connect Google and create/reuse developer test report") }
        Text(googleResult)
    }
}
