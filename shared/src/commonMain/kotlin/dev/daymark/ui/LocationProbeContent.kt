package dev.daymark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.daymark.location.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Temporary developer probe; no profile, attendance or raw position is saved. */
@Composable
fun LocationProbeContent(
    gateway: LocationGateway,
    permissions: LocationPermissionPort,
    backgroundControls: Boolean = false,
    footer: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var caps by remember { mutableStateOf(permissions.current()) }
    var status by remember { mutableStateOf("No location requested") }
    var busy by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf<Job?>(null) }
    fun run(action: suspend () -> String) {
        if (busy) return
        busy = true
        request = scope.launch {
            try { status = action() }
            catch (cancelled: CancellationException) { status = "Cancelled"; throw cancelled }
            catch (_: Exception) { status = "Native request unavailable" }
            finally { caps = permissions.current(); busy = false }
        }
    }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Native location probe", style = MaterialTheme.typography.headlineSmall)
                Text("No attendance is written. Each check uses one request for at most 30 seconds. Coordinates are not displayed or saved.")
                Text("Foreground=${caps.foregroundGranted} · Precise=${caps.preciseGranted} · Background=${caps.backgroundGranted} · Services=${caps.servicesEnabled}")
                Button(enabled = !busy, onClick = { run {
                    val result = permissions.requestForeground()
                    "Foreground=${result.foregroundGranted}; precise=${result.preciseGranted}"
                } }) { Text("Grant foreground location") }
                OutlinedButton(enabled = !busy, onClick = { permissions.openSystemSettings() }) { Text("Open app settings") }
                OutlinedButton(enabled = !busy, onClick = { caps = permissions.current() }) { Text("Refresh permission state") }
                Button(enabled = !busy, onClick = { run { probeDescription(gateway.requestFix(30.seconds)) } }) { Text("Check location once") }
                OutlinedButton(enabled = busy, onClick = { request?.cancel() }) { Text("Cancel check") }
                if (backgroundControls) {
                    Text("Background access is optional. This development probe can make one bounded check while the app is not visible. System power restrictions can prevent a fix.")
                    OutlinedButton(enabled = !busy, onClick = { run {
                        "Background=${permissions.requestBackground().backgroundGranted}"
                    } }) { Text("Grant background location") }
                }
                Text(status, style = MaterialTheme.typography.bodyMedium)
                footer()
            }
        }
    }
}

fun probeDescription(result: LocationResult): String = when (result) {
    is LocationResult.Failure -> "Result: ${result.reason}; no attendance saved"
    is LocationResult.Fix -> "Sample: accuracy=${result.horizontalAccuracyMetres} m; age=${result.ageAtReceipt ?: "unknown"}; no attendance saved"
}
