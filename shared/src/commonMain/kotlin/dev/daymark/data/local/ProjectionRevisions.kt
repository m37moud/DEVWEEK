package dev.daymark.data.local

import dev.daymark.data.local.dao.DirtyProjectionDao
import dev.daymark.models.reporting.ProjectionKey
import dev.daymark.models.reporting.storageKey

internal suspend fun DirtyProjectionDao.markAttendanceSummariesDirty(trackerId: String) {
    markDirty(trackerId, ProjectionKey.Overview.storageKey())
    markDirty(trackerId, ProjectionKey.AttendancePeriods.storageKey())
}

/** Caller must hold a write transaction so data changes and projection revisions remain atomic. */
internal suspend fun DirtyProjectionDao.markDirty(trackerId: String, key: String) {
    val before = dirtyProjection(trackerId, key)
    putDirtyProjection(
        before?.copy(revision = before.revision + 1) ?: DirtyProjectionEntity(
            trackerId,
            key,
            1
        )
    )
}
