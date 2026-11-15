package dev.daymark.data.local

import dev.daymark.data.local.dao.DirtyProjectionDao

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
