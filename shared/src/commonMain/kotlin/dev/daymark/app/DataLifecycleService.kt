package dev.daymark.app

import androidx.room3.withWriteTransaction
import dev.daymark.data.*
import dev.daymark.reporting.ReportStore
import dev.daymark.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface DeletionResult { data object Deleted : DeletionResult; data object Failed : DeletionResult }
class DataLifecycleService(private val db: DaymarkDatabase,private val reports: ReportStore,
    private val stopExternal: suspend () -> Unit,private val forgetCredentials: suspend () -> Unit,
    private val clearWidget: suspend () -> Boolean) {
    private val mutex=Mutex()
    private val deletingState=MutableStateFlow(false)
    val deleting=deletingState.asStateFlow()
    /** Invoked only by the confirmed dialog action. No Google delete/revoke call exists here. */
    suspend fun deleteLocalData(): DeletionResult = mutex.withLock {
        deletingState.value=true
        try {
            reports.invalidateBinding()
            db.withWriteTransaction {
                db.dao().tracker()?.let { db.dao().putTracker(it.copy(automaticEnabled=false,changeRevision=it.changeRevision+1)) }
            }
            SettingsRepository(db).update(remindersEnabled=false)
            stopExternal()
            forgetCredentials()
            check(clearWidget()) { "widget_clear_failed" }
            db.withWriteTransaction { db.dao().deleteAllLocalRows() }
            DeletionResult.Deleted
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { DeletionResult.Failed }
        finally { deletingState.value=false }
    }
}
