package dev.daymark.reporting

import androidx.room3.withWriteTransaction
import dev.daymark.auth.*
import dev.daymark.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

sealed interface ConnectionResult {
    data class Connected(val binding: ReportBinding) : ConnectionResult
    data object PendingCreation : ConnectionResult
    data class SelectionRequired(val reports: List<RemoteReport>) : ConnectionResult
    data object Cancelled : ConnectionResult
    data object AuthorizationRequired : ConnectionResult
    data class Failed(val errorKey: String) : ConnectionResult
}

/** All interactive authorization comes from these explicit UI actions. No remote deletion API is exposed. */
class ConnectionService(private val db: DaymarkDatabase,private val access: GoogleAccess,
    private val transport: GoogleReportTransport,private val store: ReportStore,
    private val onConnected: suspend () -> Unit = {},private val onDisconnect: suspend () -> Unit = {}) {
    private val actions=Mutex()
    suspend fun connect(): ConnectionResult = connectInternal()
    suspend fun reconnect(): ConnectionResult = try {
        store.invalidateBinding()
        onDisconnect()
        access.forget()
        connectInternal()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { ConnectionResult.Failed("connection_failed") }
    suspend fun selectReport(reportId: String): ConnectionResult = connectInternal(selected=reportId)
    suspend fun createReplacementReport(): ConnectionResult = connectInternal(replacement=true)
    suspend fun rebuildReport(): ConnectionResult = connectInternal(repair=true)
    suspend fun disconnect() {
        // Fence callbacks before cancelling foreground/native operations; never wait for the interactive mutex.
        store.invalidateBinding()
        onDisconnect()
        access.forget()
    }
    private suspend fun valid(tracker: String,generation: String): Boolean = db.dao().tracker()?.id == tracker &&
        db.dao().setting("report-generation:$tracker")?.value == generation
    private suspend fun connectInternal(selected: String? = null,replacement: Boolean = false,repair: Boolean = false): ConnectionResult = actions.withLock {
        try {
            val tracker=db.dao().tracker() ?: return@withLock ConnectionResult.Failed("no_tracker")
            val previous=store.activeBinding()
            val generation=store.beginConnection() ?: return@withLock ConnectionResult.Failed("no_tracker")
            val token=when (val result=access.accessToken(interactive=true)) {
                is AccessResult.Granted -> result.token
                AccessResult.Cancelled -> return@withLock ConnectionResult.Cancelled
                else -> return@withLock ConnectionResult.AuthorizationRequired
            }
            if (!valid(tracker.id,generation)) return@withLock ConnectionResult.Cancelled
            val account=transport.accountReference(token)
            if (!valid(tracker.id,generation)) return@withLock ConnectionResult.Cancelled
            val found=transport.discover(tracker.id,token)
            if (!valid(tracker.id,generation)) return@withLock ConnectionResult.Cancelled
            val choices=when (found) {
                ReportDiscovery.None -> emptyList()
                is ReportDiscovery.Found -> listOf(found.report)
                is ReportDiscovery.Multiple -> found.reports
            }
            val creationKey="report-creation:${tracker.id}:$account"
            val report=when {
                repair -> {
                    if (previous?.accountReference != account) return@withLock ConnectionResult.Failed("account_changed")
                    choices.find { it.id == previous.spreadsheetId } ?: return@withLock ConnectionResult.Failed("report_unavailable")
                }
                selected != null -> choices.find { it.id == selected } ?: return@withLock ConnectionResult.Failed("selection_changed")
                !replacement && choices.size>1 -> return@withLock ConnectionResult.SelectionRequired(choices)
                !replacement && choices.size==1 -> choices.single()
                else -> {
                    if (!replacement && db.dao().setting(creationKey) != null) return@withLock ConnectionResult.PendingCreation
                    // Persist uncertainty before POST. Even a killed process must search rather than blindly create again.
                    db.withWriteTransaction {
                        check(valid(tracker.id,generation)) { "stale_connection" }
                        db.dao().putSetting(SettingEntity(creationKey,generation))
                    }
                    when (val created=transport.create("Daymark attendance / سجل الحضور",tracker.id,token)) {
                        is ReportCreation.Ready -> created.report
                        ReportCreation.Ambiguous -> return@withLock ConnectionResult.PendingCreation
                        ReportCreation.Unavailable -> return@withLock ConnectionResult.Failed("report_unavailable")
                    }
                }
            }
            if (!valid(tracker.id,generation)) return@withLock ConnectionResult.Cancelled
            val binding=ReportBinding(tracker.id,generation,account,report.id,report.schemaVersion ?: 1,
                annualTabIds=if (repair) previous!!.annualTabIds else emptyMap(),
                managedTabIds=if (repair) previous!!.managedTabIds else emptyMap(),repairRequested=repair)
            store.bind(binding)
            if (store.activeBinding() != binding) return@withLock ConnectionResult.Cancelled
            db.withWriteTransaction { if (valid(tracker.id,generation)) db.dao().deleteSetting(creationKey) }
            onConnected()
            ConnectionResult.Connected(binding)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (known: ReportAccessException) { if (known.errorKey=="authorization_required") ConnectionResult.AuthorizationRequired else ConnectionResult.Failed(known.errorKey) }
        catch (_: Exception) { ConnectionResult.Failed("connection_failed") }
    }
}
