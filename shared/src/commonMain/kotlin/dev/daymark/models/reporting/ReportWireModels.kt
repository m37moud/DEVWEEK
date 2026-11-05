package dev.daymark.models.reporting

import kotlin.time.Duration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

data class RemoteReport(
    val id: String,
    val tabIds: Map<String, Int>,
    val schemaVersion: Int?,
    val requiredRows: Map<String, Int> = emptyMap(),
    val repairHeaders: Boolean = false
)

@Serializable
data class RawRange(val a1: String, val cells: List<List<JsonPrimitive>>) {
    override fun toString(): String = "RawRange(cells=<redacted>)"
}

data class RawPatch(val ranges: List<RawRange>) {
    override fun toString(): String = "RawPatch(rangeCount=${ranges.size})"
}

data class RemoteSnapshot(val ranges: Map<String, List<List<JsonPrimitive>>>) {
    override fun toString(): String = "RemoteSnapshot(rangeCount=${ranges.size})"
}

sealed interface ReportDiscovery {
    data object None : ReportDiscovery
    data class Found(val report: RemoteReport) : ReportDiscovery
    data class Multiple(val reports: List<RemoteReport>) : ReportDiscovery
}

sealed interface ReportCreation {
    data class Ready(val report: RemoteReport) : ReportCreation
    data object Ambiguous : ReportCreation
    data object Unavailable : ReportCreation
}

sealed interface RemoteWriteOutcome {
    data object Acknowledged : RemoteWriteOutcome
    data class Retryable(val retryAfter: Duration?) : RemoteWriteOutcome
    data object Ambiguous : RemoteWriteOutcome
    data object AuthorizationRequired : RemoteWriteOutcome
    data object SchemaConflict : RemoteWriteOutcome
    data object ReportUnavailable : RemoteWriteOutcome
}

class ReportAccessException(val errorKey: String) : Exception(errorKey)
