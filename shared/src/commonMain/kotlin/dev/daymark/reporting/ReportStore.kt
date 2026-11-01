package dev.daymark.reporting

import kotlin.time.Instant

interface ReportStore {
    suspend fun activeBinding(): ReportBinding?
    suspend fun beginConnection(): String?
    suspend fun bind(binding: ReportBinding)
    suspend fun updateBinding(binding: ReportBinding): Boolean
    suspend fun buildBatch(binding: ReportBinding, maxRows: Int): ReportBatch?
    suspend fun journalBeforeDispatch(batch: ReportBatch)
    suspend fun markDispatched(batch: ReportBatch)
    suspend fun acknowledge(batch: ReportBatch)
    suspend fun markAmbiguous(batch: ReportBatch)
    suspend fun invalidateBinding()
    suspend fun localProjections(binding: ReportBinding): List<ReportProjection>
    suspend fun markDirty(keys: Set<ProjectionKey>)
    suspend fun saveRetry(binding: ReportBinding, attempts: Int, nextAt: Instant?, error: String?)
    suspend fun retryState(binding: ReportBinding): RetryState
}
data class RetryState(val attempts: Int = 0, val nextAt: Instant? = null, val error: String? = null)
