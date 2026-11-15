package dev.daymark.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import dev.daymark.data.local.SettingEntity
import dev.daymark.data.local.TrackerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM trackers LIMIT 1")
    suspend fun tracker(): TrackerEntity?

    @Insert
    suspend fun insertTracker(tracker: TrackerEntity)

    @Upsert
    suspend fun putTracker(tracker: TrackerEntity)

    @Query("SELECT * FROM trackers LIMIT 1")
    fun observeTracker(): Flow<TrackerEntity?>

    @Query("UPDATE trackers SET changeRevision = changeRevision + 1 WHERE id = :trackerId")
    suspend fun advanceRevision(trackerId: String)

    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun setting(key: String): SettingEntity?

    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun observeSetting(key: String): Flow<SettingEntity?>

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)

    @Upsert
    suspend fun putSetting(setting: SettingEntity)
}
