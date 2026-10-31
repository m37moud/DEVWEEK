package dev.daymark.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.daymark.location.*
import dev.daymark.ui.probeDescription
import kotlin.time.Duration.Companion.seconds

internal const val PROBE_PREFS = "daymark-native-probe"
private object ProbeClaimLock

class LocationProbeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PROBE_PREFS, 0)
        val claimed = synchronized(ProbeClaimLock) {
            if (prefs.getString("claim", null) == id.toString()) false
            else prefs.edit().putString("claim", id.toString()).commit()
        }
        if (!claimed) return Result.success()
        val start = System.currentTimeMillis()
        val capabilities = androidLocationCapabilities(applicationContext)
        val result = if (!capabilities.backgroundGranted || !capabilities.preciseGranted)
            LocationResult.Failure(LocationFailure.PERMISSION_DENIED)
        else AndroidLocationGateway(applicationContext).requestFix(30.seconds)
        val delay = start - inputData.getLong("queuedAt", start) - 60_000
        val text = "${probeDescription(result)}; scheduling delay after earliest time=${delay.coerceAtLeast(0)} ms"
        prefs.edit().putString("lastResult", text).commit()
        return Result.success() // Probe failure never retries location.
    }
}
