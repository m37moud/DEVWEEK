package dev.daymark.android

import android.content.Context
import androidx.work.*
import dev.daymark.reporting.SyncPassResult
import dev.daymark.reporting.SyncTrigger
import java.util.concurrent.TimeUnit

internal const val REPORT_WORK="daymark-report-sync"
internal fun enqueueReportSync(context: Context,delayMillis: Long = 0) {
    val request=OneTimeWorkRequestBuilder<ReportSyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInitialDelay(delayMillis.coerceAtLeast(0),TimeUnit.MILLISECONDS).addTag(REPORT_WORK).build()
    WorkManager.getInstance(context).enqueueUniqueWork(REPORT_WORK,ExistingWorkPolicy.APPEND_OR_REPLACE,request)
}
/** Network-only work. It neither obtains a location gateway nor starts attendance scheduling. */
class ReportSyncWorker(context: Context,parameters: WorkerParameters) : CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val runtime=(applicationContext as DaymarkApplication).graph.reporting
        val result=runtime.runPass(SyncTrigger.ANDROID_WORK)
        if (result==SyncPassResult.Pending && runtime.nextDelay()!=null && runAttemptCount<5) return Result.retry()
        return Result.success()
    }
}
