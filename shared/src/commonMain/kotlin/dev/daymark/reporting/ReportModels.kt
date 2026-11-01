package dev.daymark.reporting

import dev.daymark.attendance.DayKey
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class ReportBinding(val trackerId: String, val connectionGeneration: String, val accountReference: String,
    val spreadsheetId: String, val schemaVersion: Int = 1, val annualTabIds: Map<Int, Int>,
    val managedTabIds: Map<String, Int> = emptyMap()) {
    fun remote() = RemoteReport(spreadsheetId, managedTabIds + annualTabIds.mapKeys { "Attendance ${it.key}" }, schemaVersion)
    override fun toString() = "ReportBinding(generation=$connectionGeneration, account=<redacted>)"
}
@Serializable
sealed interface ProjectionKey {
    @Serializable data class Day(val trackerId: String, val date: LocalDate) : ProjectionKey {
        constructor(key: DayKey) : this(key.trackerId, key.date)
    }
    @Serializable data object TimeOff : ProjectionKey
    @Serializable data object Overview : ProjectionKey
}
internal fun ProjectionKey.storageKey(): String = when (this) {
    is ProjectionKey.Day -> "DAY:$date"
    ProjectionKey.TimeOff -> "TIME_OFF"
    ProjectionKey.Overview -> "OVERVIEW"
}
@Serializable
data class ReportProjection(val key: ProjectionKey, val revision: Long, val ranges: List<RawRange>)
@Serializable
data class ReportBatch(val batchIdentity: String, val binding: ReportBinding, val projections: List<ReportProjection>) {
    override fun toString() = "ReportBatch(identity=$batchIdentity, projections=${projections.size})"
}
enum class SyncTrigger { LAUNCH, RESUME, LOCAL_CHANGE, CONNECTIVITY, RECONNECT, RETRY, ANDROID_WORK }
sealed interface SyncPassResult {
    data object Idle : SyncPassResult
    data object Synced : SyncPassResult
    data object Pending : SyncPassResult
    data object AuthorizationRequired : SyncPassResult
    data object SchemaConflict : SyncPassResult
    data object ReportUnavailable : SyncPassResult
    data object StaleBinding : SyncPassResult
}
