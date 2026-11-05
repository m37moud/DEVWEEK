package dev.daymark.data.local.application

import androidx.room3.withWriteTransaction
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.deleteAllLocalRows
import dev.daymark.data.local.settings.SettingsRepository
import dev.daymark.models.DeletionResult
import dev.daymark.reporting.ReportStore
import dev.daymark.repository.DataLifecycleDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DataLifecycleService(
    private val db: DaymarkDatabase, private val reports: ReportStore,
    private val stopExternal: suspend () -> Unit, private val forgetCredentials: suspend () -> Unit,
    private val clearWidget: suspend () -> Boolean,
    private val settings: SettingsRepository = SettingsRepository(db)
) : DataLifecycleDataSource {
    private val mutex = Mutex()
    private val deletingState = MutableStateFlow(false)
    override val deleting = deletingState.asStateFlow()

    /** Invoked only by the confirmed dialog action. No Google delete/revoke call exists here. */
    override suspend fun deleteLocalData(): DeletionResult = mutex.withLock {
        deletingState.value = true
        try {
            reports.invalidateBinding()
            db.withWriteTransaction {
                db.dao().tracker()?.let {
                    db.dao().putTracker(
                        it.copy(
                            automaticEnabled = false,
                            changeRevision = it.changeRevision + 1
                        )
                    )
                }
            }
            settings.update(remindersEnabled = false)
            stopExternal()
            forgetCredentials()
            check(clearWidget()) { "widget_clear_failed" }
            db.withWriteTransaction { db.dao().deleteAllLocalRows() }
            DeletionResult.Deleted
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DeletionResult.Failed
        } finally {
            deletingState.value = false
        }
    }
}
