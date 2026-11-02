package dev.daymark.reporting

import dev.daymark.auth.*
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive

enum class GoogleProbeOutcome { VERIFIED, NEEDS_INTERACTION, CANCELLED, AMBIGUOUS, MULTIPLE_REPORTS, UNAVAILABLE }

/** Developer-only callers must explain that this creates/reuses a retained test spreadsheet. */
class GoogleConnectivityProbe(native: NativeGoogleAuth) {
    private val access = GoogleAccess(native)
    suspend fun run(): GoogleProbeOutcome = HttpClient().use { client ->
        runGoogleConnectivityProbe(access, GoogleReportTransport(client))
    }
}

internal suspend fun runGoogleConnectivityProbe(access: GoogleAccess, transport: GoogleReportTransport): GoogleProbeOutcome {
    val token = when (val result = access.accessToken(interactive = true)) {
        is AccessResult.Granted -> result.token
        AccessResult.NeedsInteraction -> return GoogleProbeOutcome.NEEDS_INTERACTION
        AccessResult.Cancelled -> return GoogleProbeOutcome.CANCELLED
        is AccessResult.Failed -> return GoogleProbeOutcome.UNAVAILABLE
    }
    return try {
        transport.accountReference(token)
        val trackerId = "daymark-development-probe-v1"
        val report = when (val found = transport.discover(trackerId, token)) {
            ReportDiscovery.None -> when (val created = transport.create("Daymark developer test", trackerId, token)) {
                is ReportCreation.Ready -> created.report
                ReportCreation.Ambiguous -> return GoogleProbeOutcome.AMBIGUOUS
                ReportCreation.Unavailable -> return GoogleProbeOutcome.UNAVAILABLE
            }
            is ReportDiscovery.Found -> found.report
            is ReportDiscovery.Multiple -> return GoogleProbeOutcome.MULTIPLE_REPORTS
        }
        val cells = listOf(listOf(JsonPrimitive("Daymark developer test"), JsonPrimitive("=1+2"), JsonPrimitive("مرحبا")))
        when (transport.write(report, RawPatch(listOf(RawRange("A1:C1", cells))), token)) {
            RemoteWriteOutcome.Acknowledged -> if (transport.read(report, listOf("A1:C1"), token).ranges["A1:C1"] == cells)
                GoogleProbeOutcome.VERIFIED else GoogleProbeOutcome.UNAVAILABLE
            RemoteWriteOutcome.AuthorizationRequired -> GoogleProbeOutcome.NEEDS_INTERACTION
            RemoteWriteOutcome.Ambiguous, is RemoteWriteOutcome.Retryable -> GoogleProbeOutcome.AMBIGUOUS
            else -> GoogleProbeOutcome.UNAVAILABLE
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { GoogleProbeOutcome.UNAVAILABLE }
}
