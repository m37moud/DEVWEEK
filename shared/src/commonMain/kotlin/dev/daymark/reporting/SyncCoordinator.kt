package dev.daymark.reporting

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

interface ReportTransport {
    suspend fun prepareTemplate(report: RemoteReport,trackerId: String,zoneId: String,years: Set<Int>,token: String): RemoteReport
    suspend fun read(report: RemoteReport,ranges: List<String>,token: String): RemoteSnapshot
    suspend fun write(report: RemoteReport,patch: RawPatch,token: String): RemoteWriteOutcome
}

/** A bounded serialized pass. No location dependency and no hidden interactive authorization. */
class SyncCoordinator(private val store: ReportStore,private val transport: ReportTransport,private val clock: Clock,
    private val token: suspend () -> String?,private val zoneId: suspend () -> String,
    private val retry: SyncRetryPolicy = SyncRetryPolicy()) {
    private val drain = Mutex()
    private var verifiedGeneration: String? = null
    private data class Verification(
        val generation: String,
        val signature: List<Triple<ProjectionKey,Long,List<String>>>,
        val rows: List<Pair<ProjectionKey,RawRange>>,
        var next: Int = 0,
    )
    private var verification: Verification? = null
    suspend fun runPass(trigger: SyncTrigger): SyncPassResult = drain.withLock {
        val initial = store.activeBinding() ?: return@withLock SyncPassResult.Idle
        var binding = initial
        var batch: ReportBatch? = null
        try {
            var state = store.retryState(binding)
            val session = trigger in setOf(SyncTrigger.LAUNCH,SyncTrigger.RESUME,SyncTrigger.RECONNECT,SyncTrigger.USER_RETRY,SyncTrigger.ANDROID_WORK)
            if (trigger in setOf(SyncTrigger.LAUNCH,SyncTrigger.RESUME,SyncTrigger.RECONNECT)) {
                verification=null
                verifiedGeneration=null
            }
            if (session) {
                // A user/lifecycle session renews the budget, never a server's future Retry-After.
                state=state.copy(attempts=0)
                store.saveRetry(binding,0,state.nextAt,state.error)
            }
            if (state.nextAt?.let { it > clock.now() } == true) return@withLock SyncPassResult.Pending
            if (state.attempts >= 6) return@withLock SyncPassResult.Pending
            if (!session && state.error in setOf("authorization_required","schema_conflict","report_unavailable"))
                return@withLock errorResult(state.error!!)
            val access = token() ?: return@withLock stopped(binding,"authorization_required")
            if (store.activeBinding() != binding) return@withLock SyncPassResult.StaleBinding
            val local = store.localProjections(binding)
            val years = local.mapNotNull { (it.key as? ProjectionKey.Day)?.date?.year }.toSet()
            val capacity=local.flatMap { it.ranges }.map { parseReportRange(it.a1) }.groupBy { it.title }.mapValues { (_,ranges) -> ranges.maxOf { it.endRow } }
            val prepared = transport.prepareTemplate(binding.remote().copy(requiredRows=capacity),binding.trackerId,zoneId(),years,access)
            val refreshed = binding.copy(annualTabIds=prepared.tabIds.filterKeys { it.startsWith("Attendance ") }.mapKeys { it.key.removePrefix("Attendance ").toInt() },managedTabIds=prepared.tabIds)
            if (!store.updateBinding(refreshed)) return@withLock SyncPassResult.StaleBinding
            binding=refreshed
            // Flatten across projection boundaries: one date must not consume one HTTP read request.
            // An interrupted read keeps its successful prefix in this process. A new foreground session
            // deliberately starts over so even previously acknowledged rows are checked again.
            if (session || verification!=null || verifiedGeneration != binding.connectionGeneration || trigger == SyncTrigger.CONNECTIVITY || state.error in setOf("ambiguous","projection_too_large")) {
                val signature=local.map { Triple(it.key,it.revision,it.ranges.map { range -> range.a1 }) }
                var work=verification
                if (work==null || work.generation!=binding.connectionGeneration || work.signature!=signature) {
                    work=Verification(binding.connectionGeneration,signature,local.flatMap { projection ->
                        projection.ranges.flatMap(::individualRows).filter { reportIssue(projection.key,it)==null }.map { projection.key to it }
                    })
                    verification=work
                }
                var requests=0
                while (work.next<work.rows.size && requests<5) {
                    val chunk=work.rows.subList(work.next,minOf(work.next+100,work.rows.size))
                    val remote=transport.read(binding.remote(),chunk.map { it.second.a1 },access)
                    if (store.activeBinding()!=binding) return@withLock SyncPassResult.StaleBinding
                    val needsRepair=mutableSetOf<ProjectionKey>()
                    for ((key,row) in chunk) {
                        val actual=remote.ranges[row.a1] ?: throw ReportAccessException("report_read_failed")
                        if (!binding.repairRequested && key is ProjectionKey.Day && actual.isNotEmpty()) {
                            val cells=actual.first()
                            if (cells.getOrNull(0)?.content?.takeIf { it.isNotEmpty() }?.let { it != binding.trackerId } == true ||
                                cells.getOrNull(1)?.content?.takeIf { it.isNotEmpty() }?.let { it != key.date.toString() } == true)
                                throw ReportAccessException("schema_conflict")
                        }
                        if (!sameCells(row.cells,actual)) needsRepair+=key
                    }
                    store.markDirty(needsRepair)
                    work.next+=chunk.size
                    requests++
                }
                if (work.next<work.rows.size) {
                    // Five data reads plus template metadata/header reads stay below the default per-user
                    // quota when continuations yield ten seconds. Quota errors retain the same cursor.
                    store.saveRetry(binding,state.attempts,clock.now()+10.seconds,"verification_pending")
                    return@withLock SyncPassResult.Pending
                }
                verification=null
                verifiedGeneration=binding.connectionGeneration
                store.saveRetry(binding,state.attempts,null,null)
            }
            var wrote=false
            // Bound one foreground pass; the lifecycle owner schedules continuation if history exceeds this budget.
            repeat(10) {
                batch=store.buildBatch(binding,100)
                val next=batch ?: run {
                    if (binding.repairRequested) store.updateBinding(binding.copy(repairRequested=false))
                    if (store.projectionIssues(binding).isNotEmpty()) return@withLock stopped(binding,"projection_too_large")
                    return@withLock if (wrote) SyncPassResult.Synced else SyncPassResult.Idle
                }
                store.journalBeforeDispatch(next)
                store.markDispatched(next)
                val result=transport.write(binding.remote(),RawPatch(next.projections.flatMap { it.ranges }),access)
                if (store.activeBinding() != binding) return@withLock SyncPassResult.StaleBinding
                when (result) {
                    RemoteWriteOutcome.Acknowledged -> { store.acknowledge(next); wrote=true; batch=null; store.saveRetry(binding,state.attempts,null,null) }
                    RemoteWriteOutcome.Ambiguous, is RemoteWriteOutcome.Retryable -> {
                        store.markAmbiguous(next)
                        val delay=retry.nextDelay(state.attempts,(result as? RemoteWriteOutcome.Retryable)?.retryAfter)
                        store.saveRetry(binding,state.attempts+1,delay?.let { clock.now()+it },"ambiguous")
                        return@withLock SyncPassResult.Pending
                    }
                    RemoteWriteOutcome.AuthorizationRequired -> return@withLock stopped(binding,"authorization_required")
                    RemoteWriteOutcome.SchemaConflict -> return@withLock stopped(binding,"schema_conflict")
                    RemoteWriteOutcome.ReportUnavailable -> return@withLock stopped(binding,"report_unavailable")
                }
            }
            SyncPassResult.Pending
        } catch (cancelled: CancellationException) {
            batch?.let { withContext(NonCancellable) { runCatching { store.markAmbiguous(it) } } }
            throw cancelled
        } catch (known: ReportAccessException) {
            if (known.errorKey in setOf("authorization_required","schema_conflict","report_unavailable","projection_too_large")) stopped(binding,known.errorKey)
            else transientFailure(binding,batch)
        } catch (_: Exception) { transientFailure(binding,batch) }
    }
    private suspend fun transientFailure(binding: ReportBinding,batch: ReportBatch?): SyncPassResult {
        runCatching {
            batch?.let { store.markAmbiguous(it) }
            val state=store.retryState(binding)
            val delay=retry.nextDelay(state.attempts,null)
            store.saveRetry(binding,state.attempts+1,delay?.let { clock.now()+it },"ambiguous")
        }
        return SyncPassResult.Pending
    }
    private suspend fun stopped(binding: ReportBinding,error: String): SyncPassResult {
        store.saveRetry(binding,store.retryState(binding).attempts,null,error); return errorResult(error)
    }
    private fun errorResult(error: String): SyncPassResult = when (error) {
        "authorization_required" -> SyncPassResult.AuthorizationRequired
        "schema_conflict" -> SyncPassResult.SchemaConflict
        "projection_too_large" -> SyncPassResult.ContentTooLarge
        else -> SyncPassResult.ReportUnavailable
    }
}
internal fun sameCells(expected: List<List<JsonPrimitive>>,actual: List<List<JsonPrimitive>>): Boolean =
    expected.indices.all { row -> expected[row].indices.all { column ->
        val want=expected[row][column]
        val got=actual.getOrNull(row)?.getOrNull(column) ?: JsonPrimitive("")
        if (!want.isString && !got.isString && want.doubleOrNull != null) want.doubleOrNull == got.doubleOrNull else want.content == got.content
    } } && actual.drop(expected.size).all { row -> row.all { it.content.isEmpty() } }
