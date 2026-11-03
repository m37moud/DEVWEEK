package dev.daymark.reporting

import androidx.room3.withWriteTransaction
import dev.daymark.app.AppLifecycleCoordinator
import dev.daymark.attendance.AttendanceRepository
import dev.daymark.auth.AccessResult
import dev.daymark.auth.GoogleAccess
import dev.daymark.data.DaymarkDatabase
import dev.daymark.data.SettingEntity
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class ReportHealth(
    val binding: ReportBinding? = null,
    val pendingCount: Int = 0,
    val result: SyncPassResult = SyncPassResult.Idle,
    val issues: List<ReportIssue> = emptyList(),
    val connectionResult: ConnectionResult? = null,
    val lastSync: String? = null,
    val busy: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportingRuntime(
    private val db: DaymarkDatabase,
    private val auth: GoogleAccess,
    private val clock: Clock,
    private val repository: AttendanceRepository,
    httpClient: HttpClient? = null,
    enqueueNetwork: () -> Unit = {},
    observeConnectivity: ((() -> Unit) -> (() -> Unit)) = ::observeReportConnectivity,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val store = RoomReportStore(db, clock)
    private val clientDelegate = lazy {
        httpClient ?: HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000; connectTimeoutMillis = 15_000; socketTimeoutMillis =
                30_000
            }
        }
    }
    private val client by clientDelegate
    private val transport by lazy { GoogleReportTransport(client) }
    val sync = SyncCoordinator(
        store,
        transport,
        clock,
        token = {
            val binding = store.activeBinding()
            val result = auth.accessToken(interactive = false)
            if (binding != null && result is AccessResult.Granted && transport.accountReference(
                    result.token
                ) == binding.accountReference
            ) result.token else null
        },
        zoneId = {
            db.dao().tracker()?.let { db.dao().versions(it.id).lastOrNull()?.schedule()?.zoneId }
                ?: "UTC"
        })
    private val state = MutableStateFlow(ReportHealth())
    val health = state.asStateFlow()
    val lifecycle =
        AppLifecycleCoordinator(scope, ::runPass, ::nextDelay, enqueueNetwork, observeConnectivity)
    val connection = ConnectionService(
        db,
        auth,
        transport,
        store,
        onConnected = { lifecycle.onReconnect(); refreshHealth() },
        onDisconnect = {
            val wasActive = lifecycle.isActive
            lifecycle.stop()
            if (wasActive) lifecycle.onActive(true)
            refreshHealth(connectionResult = null)
        })

    init {
        scope.launch {
            db.dao().observeTracker().flatMapLatest { tracker ->
                if (tracker == null) flowOf(emptyList()) else db.dao()
                    .observeDirtyProjections(tracker.id)
            }.distinctUntilChangedBy { rows -> rows.map { it.projectionKey to it.revision } }
                .onEach {
                    if (store.activeBinding() != null) lifecycle.onLocalChange()
                    refreshHealth()
                }.catch { error ->
                if (error is CancellationException) throw error
                state.update { it.copy(result = SyncPassResult.Pending) }
            }.collect()
        }
    }

    suspend fun runPass(trigger: SyncTrigger): SyncPassResult {
        state.update { it.copy(busy = true) }
        try {
            repository.reconcile(clock.now())
            val requestedGeneration = store.activeBinding()?.connectionGeneration
            val result = sync.runPass(trigger)
            if (result == SyncPassResult.Synced) {
                val binding = store.activeBinding()
                db.withWriteTransaction {
                    if (binding != null && binding.connectionGeneration == requestedGeneration && db.dao()
                            .binding(binding.trackerId)?.connectionGeneration == requestedGeneration
                    )
                        db.dao().putSetting(
                            SettingEntity(
                                "report-last-sync:${binding.trackerId}:${binding.connectionGeneration}",
                                clock.now().toString()
                            )
                        )
                }
            }
            refreshHealth(result)
            return result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.update { it.copy(result = SyncPassResult.Pending) }; return SyncPassResult.Pending
        } finally {
            state.update { it.copy(busy = false) }
        }
    }

    suspend fun refreshHealth(
        result: SyncPassResult = state.value.result,
        connectionResult: ConnectionResult? = state.value.connectionResult
    ) {
        val binding = store.activeBinding()
        val dirty = binding?.let {
            db.dao().dirtyProjections(it.trackerId)
                .count { row -> row.revision > row.acknowledgedRevision }
        } ?: 0
        val issues = binding?.let { store.projectionIssues(it) }.orEmpty()
        val last = binding?.let {
            db.dao().setting("report-last-sync:${it.trackerId}:${it.connectionGeneration}")?.value
        }
        state.update { old ->
            old.copy(
                binding = binding, pendingCount = dirty,
                result = if (old.binding?.connectionGeneration != binding?.connectionGeneration) SyncPassResult.Idle else result,
                connectionResult = connectionResult, lastSync = last, issues = issues
            )
        }
    }

    fun close() {
        lifecycle.onActive(false)
        scope.cancel()
        if (clientDelegate.isInitialized()) client.close()
    }

    suspend fun nextDelay(): Long? = try {
        val binding = store.activeBinding()
        if (binding == null) null else {
            val retry = store.retryState(binding)
            if (retry.attempts >= 6 || retry.error in setOf(
                    "authorization_required",
                    "schema_conflict",
                    "report_unavailable",
                    "projection_too_large"
                )
            ) null
            else retry.nextAt?.let { (it - clock.now()).inWholeMilliseconds.coerceAtLeast(1) }
                ?: 1_000
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

expect fun observeReportConnectivity(onAvailable: () -> Unit): () -> Unit
