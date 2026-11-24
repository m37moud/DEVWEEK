package dev.daymark.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.daymark.data.local.DirtyProjectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DirtyProjectionDao {
    @Query("SELECT * FROM dirty_projections WHERE trackerId = :trackerId ORDER BY projectionKey")
    suspend fun dirtyProjections(trackerId: String): List<DirtyProjectionEntity>

    @Query("SELECT * FROM dirty_projections WHERE trackerId = :trackerId ORDER BY projectionKey")
    fun observeDirtyProjections(trackerId: String): Flow<List<DirtyProjectionEntity>>

    @Query("SELECT * FROM dirty_projections WHERE trackerId = :trackerId AND projectionKey = :key")
    suspend fun dirtyProjection(trackerId: String, key: String): DirtyProjectionEntity?

    @Query("SELECT * FROM dirty_projections WHERE trackerId = :trackerId AND revision > acknowledgedRevision")
    suspend fun pendingProjections(trackerId: String): List<DirtyProjectionEntity>

    @Query("SELECT COUNT(*) FROM dirty_projections WHERE trackerId = :trackerId AND revision > acknowledgedRevision")
    suspend fun pendingCount(trackerId: String): Int

    @Query("SELECT projectionKey FROM dirty_projections WHERE trackerId = :trackerId")
    suspend fun projectionKeys(trackerId: String): List<String>

    @Upsert
    suspend fun putDirtyProjection(projection: DirtyProjectionEntity)
}
