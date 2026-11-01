package dev.daymark.reporting

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dev.daymark.attendance.*
import dev.daymark.data.*
import dev.daymark.plan.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*
import kotlin.time.Instant
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** All journal and revision fences share the transaction that owns the authoritative local records. */
class RoomReportStore(private val db: DaymarkDatabase, private val clock: Clock) : ReportStore {
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun generationKey(id: String) = "report-generation:$id"
    private fun retryKey(binding: ReportBinding) = "report-retry:${binding.trackerId}"
    private suspend fun current(): ReportBinding? {
        val tracker = dao.tracker() ?: return null
        val row = dao.binding(tracker.id) ?: return null
        val binding = runCatching { json.decodeFromString<ReportBinding>(row.annualTabIdsJson) }.getOrNull() ?: return null
        return binding.takeIf { it.trackerId == tracker.id && it.connectionGeneration == dao.setting(generationKey(tracker.id))?.value }
    }
    override suspend fun activeBinding() = db.withReadTransaction { current() }
    override suspend fun beginConnection(): String? = db.withWriteTransaction {
        val tracker = dao.tracker() ?: return@withWriteTransaction null
        val generation = Uuid.random().toString()
        dao.putSetting(SettingEntity(generationKey(tracker.id),generation))
        dao.deleteBinding(tracker.id)
        generation
    }
    override suspend fun bind(binding: ReportBinding) = db.withWriteTransaction {
        if (dao.tracker()?.id == binding.trackerId && dao.setting(generationKey(binding.trackerId))?.value == binding.connectionGeneration) {
            putBinding(binding)
            ensureProjections(binding)
            dao.dirtyProjections(binding.trackerId).forEach { dao.putDirtyProjection(it.copy(acknowledgedRevision=0)) }
        }
    }
    private suspend fun putBinding(binding: ReportBinding) = dao.putBinding(ReportBindingEntity(binding.trackerId,binding.connectionGeneration,
        binding.accountReference,binding.spreadsheetId,binding.schemaVersion,json.encodeToString(binding)))
    override suspend fun updateBinding(binding: ReportBinding): Boolean = db.withWriteTransaction {
        val old = current()
        if (old == null || old.copy(annualTabIds=binding.annualTabIds,managedTabIds=binding.managedTabIds,repairRequested=if (old.repairRequested) binding.repairRequested else false) != binding) false
        else { putBinding(binding); true }
    }
    override suspend fun invalidateBinding() { beginConnection() }
    private suspend fun ensureProjections(binding: ReportBinding) {
        val keys = listOf("OVERVIEW","TIME_OFF") + dao.days(binding.trackerId).map { "DAY:${it.date}" }
        keys.forEach { if (dao.dirtyProjection(binding.trackerId,it) == null) dao.markDirty(binding.trackerId,it) }
    }
    private suspend fun projections(binding: ReportBinding): List<ReportProjection> {
        if (current() != binding) return emptyList()
        val state = dao.planning(clock.now()) ?: return emptyList()
        val dirty = dao.dirtyProjections(binding.trackerId).associateBy { it.projectionKey }
        val days = dirty.keys.filter { it.startsWith("DAY:") }.map { LocalDate.parse(it.removePrefix("DAY:")) }.sorted().map { date ->
            val plan = state.plan(date)
            val evidence = state.rows[date]?.evidence()
            // Stable across reads; a new session must not rewrite every row just because wall time advanced.
            val updated = evidence?.updatedAt ?: Instant.parse(state.tracker.createdAt)
            projectDay(HistoryRecord(plan,evidence,state.outcome(date)),dirty.getValue("DAY:$date").revision,updated)
        }
        val capacityKey = "report-time-off-capacity:${binding.trackerId}"
        val capacity = maxOf(1,state.entries.size,dao.setting(capacityKey)?.value?.toIntOrNull() ?: 0)
        val timeOff = projectTimeOff(state.entries,dirty["TIME_OFF"]?.revision ?: 1,capacity)
        val actual = state.rows.keys.map { state.outcome(it).status }
        fun field(key: String,label: String,value: String) = Triple(key,label,JsonPrimitive(value))
        val overview = projectOverview(listOf(
            field("trackerId","Tracker / المتابع",state.tracker.id),field("name","Name / الاسم",state.tracker.displayName),
            field("category","Category / النوع",state.active.category),field("zoneId","Time zone / المنطقة الزمنية",state.active.schedule().zoneId),
            field("present","Present / حاضر",actual.count { it == AttendanceStatus.PRESENT }.toString()),
            field("late","Late / متأخر",actual.count { it == AttendanceStatus.LATE }.toString()),
            field("absent","Absent / غائب",actual.count { it == AttendanceStatus.ABSENT }.toString()),
            field("vacationBalance","Vacation balance / رصيد الإجازة",if (state.active.category == "WORK") state.balance().toString() else "")),dirty["OVERVIEW"]?.revision ?: 1)
        return days + timeOff + overview
    }
    override suspend fun localProjections(binding: ReportBinding): List<ReportProjection> = db.withWriteTransaction {
        if (current() != binding) return@withWriteTransaction emptyList()
        ensureProjections(binding)
        projections(binding)
    }
    override suspend fun projectionIssues(binding: ReportBinding): List<ReportIssue> = db.withReadTransaction {
        projections(binding).flatMap { projection -> projection.ranges.flatMap(::individualRows).mapNotNull { reportIssue(projection.key,it) } }.distinct()
    }
    private suspend fun acknowledgedRows(binding: ReportBinding, key: ProjectionKey, revision: Long): Set<String> =
        dao.batches(binding.trackerId).filter { it.connectionGeneration == binding.connectionGeneration && it.status == "ACKNOWLEDGED" }
            .flatMap { json.decodeFromString<ReportBatch>(it.payloadJson).projections }
            .filter { it.key == key && it.revision == revision }.flatMap { it.ranges.flatMap(::individualRows) }.map { it.a1 }.toSet()
    override suspend fun buildBatch(binding: ReportBinding, maxRows: Int): ReportBatch? = db.withWriteTransaction {
        require(maxRows in 1..500)
        if (current() != binding) return@withWriteTransaction null
        ensureProjections(binding)
        val dirty = dao.dirtyProjections(binding.trackerId).associateBy { it.projectionKey }
        val selected = mutableListOf<ReportProjection>()
        var rows = 0
        var bytes = 0
        for (projection in projections(binding)) {
            val state = dirty[projection.key.storageKey()] ?: continue
            if (state.revision <= state.acknowledgedRevision) continue
            val covered = acknowledgedRows(binding,projection.key,projection.revision)
            val ranges = mutableListOf<RawRange>()
            for (row in projection.ranges.flatMap(::individualRows)) {
                if (row.a1 in covered || reportIssue(projection.key,row)!=null) continue
                val size = json.encodeToString(row).encodeToByteArray().size + 512
                if (rows == maxRows || bytes + size > 1_900_000) break
                ranges += row; rows++; bytes += size
            }
            if (ranges.isNotEmpty()) selected += projection.copy(ranges=ranges)
            if (rows == maxRows || bytes >= 1_800_000) break
        }
        selected.takeIf { it.isNotEmpty() }?.let { ReportBatch(Uuid.random().toString(),binding,it) }
    }
    override suspend fun journalBeforeDispatch(batch: ReportBatch) = db.withWriteTransaction {
        check(current() == batch.binding) { "stale_binding" }
        check(dao.batches(batch.binding.trackerId).none { it.batchIdentity == batch.batchIdentity }) { "duplicate_batch" }
        // Reserve old table capacity before a possibly ambiguous write so later deletion clears every published row.
        val offRows = batch.projections.filter { it.key == ProjectionKey.TimeOff }.flatMap { it.ranges }.maxOfOrNull { parseReportRange(it.a1).endRow - 2 }
        if (offRows != null) {
            val key = "report-time-off-capacity:${batch.binding.trackerId}"
            dao.putSetting(SettingEntity(key,maxOf(offRows,dao.setting(key)?.value?.toIntOrNull() ?: 0).toString()))
        }
        dao.putBatch(ReportBatchEntity(batch.batchIdentity,batch.binding.trackerId,batch.binding.connectionGeneration,json.encodeToString(batch),"PREPARED",clock.now().toString()))
    }
    private suspend fun status(batch: ReportBatch, value: String) {
        val row = dao.batches(batch.binding.trackerId).find { it.batchIdentity == batch.batchIdentity } ?: return
        if (row.connectionGeneration != batch.binding.connectionGeneration || row.payloadJson != json.encodeToString(batch)) return
        if (row.status == "ACKNOWLEDGED") return
        dao.putBatch(row.copy(status=value,updatedAt=clock.now().toString()))
    }
    override suspend fun markDispatched(batch: ReportBatch) = db.withWriteTransaction {
        check(current() == batch.binding) { "stale_binding" }; status(batch,"DISPATCHED")
    }
    override suspend fun markAmbiguous(batch: ReportBatch) = db.withWriteTransaction { status(batch,"AMBIGUOUS") }
    override suspend fun acknowledge(batch: ReportBatch) = db.withWriteTransaction {
        if (current() != batch.binding) return@withWriteTransaction
        val journal = dao.batches(batch.binding.trackerId).find { it.batchIdentity == batch.batchIdentity } ?: return@withWriteTransaction
        if (journal.status != "DISPATCHED") return@withWriteTransaction
        status(batch,"ACKNOWLEDGED")
        for (projection in projections(batch.binding)) {
            val dirty = dao.dirtyProjection(batch.binding.trackerId,projection.key.storageKey()) ?: continue
            val covered = acknowledgedRows(batch.binding,projection.key,projection.revision)
            if (projection.ranges.flatMap(::individualRows).all { it.a1 in covered })
                dao.putDirtyProjection(dirty.copy(acknowledgedRevision=projection.revision))
        }
    }
    override suspend fun markDirty(keys: Set<ProjectionKey>) = db.withWriteTransaction {
        val tracker = dao.tracker() ?: return@withWriteTransaction
        keys.forEach { key -> if (key !is ProjectionKey.Day || key.trackerId == tracker.id) {
            val old = dao.dirtyProjection(tracker.id,key.storageKey())
            if (old == null) dao.markDirty(tracker.id,key.storageKey()) else dao.putDirtyProjection(old.copy(acknowledgedRevision=0))
        } }
        dao.batches(tracker.id).filter { it.status == "ACKNOWLEDGED" }.forEach { row ->
            if (json.decodeFromString<ReportBatch>(row.payloadJson).projections.any { it.key in keys }) dao.deleteBatch(row.batchIdentity)
        }
    }
    override suspend fun saveRetry(binding: ReportBinding, attempts: Int, nextAt: Instant?, error: String?) = db.withWriteTransaction {
        if (current() == binding) dao.putSetting(SettingEntity(retryKey(binding),buildJsonObject {
            put("generation",binding.connectionGeneration); put("attempts",attempts); put("nextAt",nextAt?.toString()); put("error",error)
        }.toString()))
    }
    override suspend fun retryState(binding: ReportBinding): RetryState = db.withReadTransaction {
        val body = dao.setting(retryKey(binding))?.value?.let { Json.parseToJsonElement(it).jsonObject }
        if (body?.get("generation")?.jsonPrimitive?.content != binding.connectionGeneration) RetryState()
        else RetryState(body.getValue("attempts").jsonPrimitive.int,body["nextAt"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse),body["error"]?.jsonPrimitive?.contentOrNull)
    }
}
internal data class ReportRange(val title: String,val startRow: Int,val endRow: Int,val startColumn: String,val endColumn: String)
internal fun parseReportRange(a1: String): ReportRange {
    val match = Regex("^'([^']+)'!([A-Z]+)([1-9][0-9]*):([A-Z]+)([1-9][0-9]*)$").matchEntire(a1) ?: throw ReportAccessException("schema_conflict")
    return ReportRange(match.groupValues[1],match.groupValues[3].toInt(),match.groupValues[5].toInt(),match.groupValues[2],match.groupValues[4]).also {
        require(it.endRow >= it.startRow)
    }
}
internal fun individualRows(range: RawRange): List<RawRange> {
    val parsed = parseReportRange(range.a1)
    require(range.cells.size == parsed.endRow-parsed.startRow+1)
    return range.cells.mapIndexed { index,row -> val at=parsed.startRow+index; RawRange("'${parsed.title}'!${parsed.startColumn}$at:${parsed.endColumn}$at",listOf(row)) }
}
