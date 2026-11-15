package dev.daymark.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.daymark.data.local.AttendanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId AND date = :date")
    suspend fun day(trackerId: String, date: String): AttendanceEntity?

    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId AND date = :date")
    fun observeDay(trackerId: String, date: String): Flow<AttendanceEntity?>

    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId ORDER BY date")
    suspend fun days(trackerId: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId ORDER BY date")
    fun observeDays(trackerId: String): Flow<List<AttendanceEntity>>

    @Query("SELECT COUNT(*) FROM attendance WHERE trackerId = :trackerId AND date = :date")
    suspend fun countDay(trackerId: String, date: String): Int

    @Upsert
    suspend fun putDay(day: AttendanceEntity)
}
