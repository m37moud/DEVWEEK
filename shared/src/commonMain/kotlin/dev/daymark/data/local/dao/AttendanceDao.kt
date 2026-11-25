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

    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId AND date BETWEEN :from AND :through ORDER BY date")
    suspend fun daysBetween(
        trackerId: String,
        from: String,
        through: String
    ): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId AND cachedStatus IN ('SCHEDULED', 'AWAITING_CHECK')")
    suspend fun awaitingOutcome(trackerId: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId AND automaticState = 'CLAIMED'")
    suspend fun claimedDays(trackerId: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId AND (automaticState = 'WAITING_RETRY' OR automaticResultPending = 1) ORDER BY date")
    suspend fun automaticRecoveryRows(trackerId: String): List<AttendanceEntity>

    @Query("SELECT date FROM attendance WHERE trackerId = :trackerId ORDER BY date")
    suspend fun dates(trackerId: String): List<String>

    @Query("SELECT COUNT(*) FROM attendance WHERE trackerId = :trackerId AND date = :date")
    suspend fun countDay(trackerId: String, date: String): Int

    @Upsert
    suspend fun putDay(day: AttendanceEntity)
}
