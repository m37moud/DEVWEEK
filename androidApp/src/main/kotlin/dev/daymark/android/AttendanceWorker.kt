package dev.daymark.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.daymark.models.attendance.DayKey
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate

class AttendanceWorker(context: Context, parameters: WorkerParameters, private val injectedJob: AndroidAttendanceJob?) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(context, parameters, null)
    override suspend fun doWork(): Result {
        val trackerId = inputData.getString("trackerId") ?: return Result.failure()
        val date = inputData.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return Result.failure()
        return try {
            val job = injectedJob ?: (applicationContext as DaymarkApplication).graph.attendanceJob()
            if (job.run(DayKey(trackerId, date))) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
