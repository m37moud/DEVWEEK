package dev.daymark.android

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.daymark.location.AndroidLocationGateway
import dev.daymark.ui.LocationProbeContent
import dev.daymark.reporting.GoogleConnectivityProbe
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
internal fun DevelopmentHost(permissions: AndroidLocationPermissions, googleAuth: AndroidGoogleAuth) {
    val context = LocalContext.current.applicationContext
    val gateway = remember(context) { AndroidLocationGateway(context) }
    var backgroundResult by remember { mutableStateOf("Background probe not scheduled") }
    var googleResult by remember { mutableStateOf("Developer report is retained in Google Drive; local OAuth registration is required.") }
    var googleBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LocationProbeContent(gateway, permissions, backgroundControls = true) {
        Button(onClick = {
            val work = OneTimeWorkRequestBuilder<LocationProbeWorker>()
                .setInitialDelay(60, TimeUnit.SECONDS)
                .setInputData(workDataOf("queuedAt" to System.currentTimeMillis()))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("daymark-native-location-probe", ExistingWorkPolicy.KEEP, work)
            backgroundResult = "One background probe queued, earliest in 60 seconds; delivery is not guaranteed"
        }) { Text("Schedule one background probe") }
        OutlinedButton(onClick = {
            backgroundResult = context.getSharedPreferences(PROBE_PREFS, 0)
                .getString("lastResult", "No background result yet") ?: "No background result yet"
        }) { Text("Read background result") }
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
