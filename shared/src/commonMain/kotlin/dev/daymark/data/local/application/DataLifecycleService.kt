package dev.daymark.data.local.application

import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.dao.LocalDataDeletionDao
import dev.daymark.data.local.dao.ProfileDao
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

// clean-code exception: constructor arity keeps cross-DAO transaction ownership explicit; revisit when responsibilities can split without changing atomicity.
class DataLifecycleService(
    private val profileDao: ProfileDao,
    private val localDataDeletionDao: LocalDataDeletionDao,
    private val transactions: DatabaseTransactions,
    private val reports: ReportStore,
    private val stopExternal: suspend () -> Unit,
    private val forgetCredentials: suspend () -> Unit,
    private val clearWidget: suspend () -> Boolean,
    private val settings: SettingsRepository
) : DataLifecycleDataSource {
    private val mutex = Mutex()
    private val deletingState = MutableStateFlow(false)
    override val deleting = deletingState.asStateFlow()

    /** Invoked only by the confirmed dialog action. No Google delete/revoke call exists here. */
    override suspend fun deleteLocalData(): DeletionResult = mutex.withLock {
        deletingState.value = true
        try {
            reports.invalidateBinding()
            transactions.withWriteTransaction {
                profileDao.tracker()?.let {
                    profileDao.putTracker(
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
            transactions.withWriteTransaction { localDataDeletionDao.deleteAllLocalRows() }
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
