package dev.daymark.data.local.reporting

import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.ReportBatchEntity
import dev.daymark.data.local.ReportBindingEntity
import dev.daymark.data.local.SettingEntity
import dev.daymark.data.local.dao.AttendanceDao
import dev.daymark.data.local.dao.DirtyProjectionDao
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.dao.ReportDao
import dev.daymark.data.local.markDirty
import dev.daymark.data.local.plan.PlanningSnapshotReader
import dev.daymark.data.local.plan.balanceYear
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.data.local.plan.historyRecord
import dev.daymark.history.isWorkedDay
import dev.daymark.models.reporting.ProjectionKey
import dev.daymark.models.reporting.RawRange
import dev.daymark.models.reporting.ReportAccessException
import dev.daymark.models.reporting.ReportBatch
import dev.daymark.models.reporting.ReportBinding
import dev.daymark.models.reporting.ReportProjection
import dev.daymark.models.reporting.storageKey
import dev.daymark.reporting.sameCells
import dev.daymark.reporting.ReportIssue
import dev.daymark.reporting.ReportStore
import dev.daymark.reporting.RetryState
import dev.daymark.reporting.reportIssue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** All journal and revision fences share the transaction that owns the authoritative local records. */
// clean-code exception: constructor arity keeps cross-DAO transaction ownership explicit; revisit when responsibilities can split without changing atomicity.
class RoomReportStore(
    private val profileDao: ProfileDao,
    private val attendanceDao: AttendanceDao,
    private val dirtyProjectionDao: DirtyProjectionDao,
    private val reportDao: ReportDao,
    private val snapshots: PlanningSnapshotReader,
    private val transactions: DatabaseTransactions,
    private val clock: Clock
) : ReportStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun generationKey(id: String) = "report-generation:$id"
    private fun retryKey(binding: ReportBinding) = "report-retry:${binding.trackerId}"
    private suspend fun current(): ReportBinding? {
        val tracker = profileDao.tracker() ?: return null
        val row = reportDao.binding(tracker.id) ?: return null
        val binding =
            runCatching { json.decodeFromString<ReportBinding>(row.annualTabIdsJson) }.getOrNull()
                ?: return null
        return binding.takeIf {
            it.trackerId == tracker.id && it.connectionGeneration == profileDao.setting(
                generationKey(tracker.id)
            )?.value
        }
    }

    override suspend fun activeBinding() = transactions.withReadTransaction { current() }
    override suspend fun beginConnection(): String? = transactions.withWriteTransaction {
        val tracker = profileDao.tracker() ?: return@withWriteTransaction null
        val generation = Uuid.random().toString()
        profileDao.putSetting(SettingEntity(generationKey(tracker.id), generation))
        reportDao.deleteBinding(tracker.id)
        generation
    }

    override suspend fun bind(binding: ReportBinding) = transactions.withWriteTransaction {
        if (profileDao.tracker()?.id == binding.trackerId && profileDao.setting(
                generationKey(
                    binding.trackerId
                )
            )?.value == binding.connectionGeneration
        ) {
            putBinding(binding)
            ensureProjections(binding)
            dirtyProjectionDao.dirtyProjections(binding.trackerId)
                .forEach { dirtyProjectionDao.putDirtyProjection(it.copy(acknowledgedRevision = 0)) }
        }
    }

    private suspend fun putBinding(binding: ReportBinding) = reportDao.putBinding(
        ReportBindingEntity(
            binding.trackerId,
            binding.connectionGeneration,
            binding.accountReference,
            binding.spreadsheetId,
            binding.schemaVersion,
            json.encodeToString(binding)
        )
    )

    override suspend fun updateBinding(binding: ReportBinding): Boolean = transactions.withWriteTransaction {
        val old = current()
        if (old == null || old.copy(
                annualTabIds = binding.annualTabIds,
                managedTabIds = binding.managedTabIds,
                repairRequested = if (old.repairRequested) binding.repairRequested else false
            ) != binding
        ) false
        else {
            putBinding(binding); true
        }
    }

    override suspend fun invalidateBinding() {
        beginConnection()
    }

    private suspend fun ensureProjections(binding: ReportBinding) {
        ensureVacationBalancePeriod(binding.trackerId)
        val keys =
            listOf("OVERVIEW", "TIME_OFF", ProjectionKey.AttendancePeriods.storageKey()) + attendanceDao.days(binding.trackerId)
                .map { "DAY:${it.date}" }
        keys.forEach {
            if (dirtyProjectionDao.dirtyProjection(
                    binding.trackerId,
                    it
                ) == null
            ) dirtyProjectionDao.markDirty(binding.trackerId, it)
        }
    }

    private suspend fun ensureVacationBalancePeriod(trackerId: String) {
        val state = snapshots.load(clock.now()) ?: return
        val year = state.balanceYear(state.vacationYear)?.toString()
        val key = "report-vacation-balance-year:$trackerId"
        if (profileDao.setting(key)?.value == year) return
        dirtyProjectionDao.markDirty(trackerId, "OVERVIEW")
        if (year == null) profileDao.deleteSetting(key)
        else profileDao.putSetting(SettingEntity(key, year))
    }

    private suspend fun projections(binding: ReportBinding): List<ReportProjection> {
        if (current() != binding) return emptyList()
        val state = snapshots.load(clock.now()) ?: return emptyList()
        val dirty =
            dirtyProjectionDao.dirtyProjections(binding.trackerId).associateBy { it.projectionKey }
        val days = dirty.keys.filter { it.startsWith("DAY:") }
            .map { LocalDate.parse(it.removePrefix("DAY:")) }.sorted().map { date ->
                val plan = state.plan(date)
                val record = state.historyRecord(plan)
                val mission = state.dayEntry(plan)?.takeIf { record.outcome.status == AttendanceStatus.WORK_MISSION }
                // Stable across reads; advancing the clock does not rewrite every row.
                val updated = mission?.updatedAt ?: record.evidence?.updatedAt ?: Instant.parse(state.tracker.createdAt)
                projectDay(
                    record,
                    dirty.getValue("DAY:$date").revision,
                    updated
                )
            }
        val timeOff = projectTimeOff(
            state.entries,
            dirty["TIME_OFF"]?.revision ?: 1,
            publishedCapacity(binding.trackerId, ProjectionKey.TimeOff)
        )
        val actual = state.rows.mapValues { (date, _) -> state.outcome(date).status }
        val statuses = actual.values
        val workDays = actual.count { (date, status) ->
            state.rows.getValue(date).category == TrackerCategory.WORK.name && status.isWorkedDay
        }
        fun field(key: String, label: String, value: String) =
            Triple(key, label, JsonPrimitive(value))

        fun count(key: String, label: String, value: Int) = Triple(key, label, JsonPrimitive(value))
        val vacationBalance = state.takeIf { it.active.category == "WORK" }?.balanceSummary()
        val vacationLabel = vacationBalance?.year?.let { "Vacation balance ($it) / رصيد الإجازة ($it)" }
            ?: "Vacation balance / رصيد الإجازة"
        val overview = projectOverview(
            listOf(
                field("trackerId", "Tracker / المتابع", state.tracker.id),
                field("name", "Name / الاسم", state.tracker.displayName),
                field("category", "Category / النوع", state.active.category),
                field("zoneId", "Time zone / المنطقة الزمنية", state.active.schedule().zoneId),
                count("present", "Present / حاضر", statuses.count { it == AttendanceStatus.PRESENT }),
                count("late", "Late / متأخر", statuses.count { it == AttendanceStatus.LATE }),
                count("absent", "Absent / غائب", statuses.count { it == AttendanceStatus.ABSENT }),
                if (vacationBalance != null) count("vacationBalance", vacationLabel, vacationBalance.remainingDays)
                else field("vacationBalance", vacationLabel, ""),
                count("workDays", "Total work days / إجمالي أيام العمل", workDays),
                count(
                    "workMissions",
                    "Work missions / المأموريات",
                    statuses.count { it == AttendanceStatus.WORK_MISSION })
            ), dirty["OVERVIEW"]?.revision ?: 1
        )
        val periodsKey = ProjectionKey.AttendancePeriods
        val periods = state.projectAttendancePeriods(
            dirty[periodsKey.storageKey()]?.revision ?: 1,
            publishedCapacity(binding.trackerId, periodsKey)
        )
        return days + timeOff + overview + periods
    }

    override suspend fun localProjections(binding: ReportBinding): List<ReportProjection> =
        transactions.withWriteTransaction {
            if (current() != binding) return@withWriteTransaction emptyList()
            ensureProjections(binding)
            projections(binding)
        }

    override suspend fun projectionIssues(binding: ReportBinding): List<ReportIssue> =
        transactions.withReadTransaction {
            projections(binding).flatMap { projection ->
                projection.ranges.flatMap(::individualRows)
                    .mapNotNull { reportIssue(projection.key, it) }
            }.distinct()
        }

    private suspend fun acknowledgedRows(
        binding: ReportBinding,
        projection: ReportProjection
    ): Set<String> {
        val acknowledged = reportDao.batches(binding.trackerId)
            .filter { it.connectionGeneration == binding.connectionGeneration && it.status == "ACKNOWLEDGED" }
            .flatMap { json.decodeFromString<ReportBatch>(it.payloadJson).projections }
            .filter { it.key == projection.key && it.revision == projection.revision }
            .flatMap { it.ranges.flatMap(::individualRows) }
        if (projection.key != ProjectionKey.AttendancePeriods) return acknowledged.map { it.a1 }.toSet()

        // A deadline or midnight can change calculated totals while a batch is in flight.
        val expected = projection.ranges.flatMap(::individualRows).associateBy { it.a1 }
        return acknowledged.filter { row ->
            expected[row.a1]?.let { sameCells(it.cells, row.cells) } == true
        }.map { it.a1 }.toSet()
    }

    override suspend fun buildBatch(binding: ReportBinding, maxRows: Int): ReportBatch? =
        transactions.withWriteTransaction {
            require(maxRows in 1..500)
            if (current() != binding) return@withWriteTransaction null
            ensureProjections(binding)
            val dirty = dirtyProjectionDao.dirtyProjections(binding.trackerId)
                .associateBy { it.projectionKey }
            val selected = mutableListOf<ReportProjection>()
            var rows = 0
            var bytes = 0
            for (projection in projections(binding)) {
                val state = dirty[projection.key.storageKey()] ?: continue
                if (state.revision <= state.acknowledgedRevision) continue
                val covered = acknowledgedRows(binding, projection)
                val ranges = mutableListOf<RawRange>()
                for (row in projection.ranges.flatMap(::individualRows)) {
                    if (row.a1 in covered || reportIssue(projection.key, row) != null) continue
                    val size = json.encodeToString(row).encodeToByteArray().size + 512
                    if (rows == maxRows || bytes + size > 1_900_000) break
                    ranges += row; rows++; bytes += size
                }
                if (ranges.isNotEmpty()) selected += projection.copy(ranges = ranges)
                if (rows == maxRows || bytes >= 1_800_000) break
            }
            selected.takeIf { it.isNotEmpty() }
                ?.let { ReportBatch(Uuid.random().toString(), binding, it) }
        }

    private fun capacityKey(trackerId: String, projection: ProjectionKey): String = when (projection) {
        ProjectionKey.TimeOff -> "report-time-off-capacity:$trackerId"
        else -> "report-attendance-period-capacity:$trackerId"
    }

    private suspend fun publishedCapacity(trackerId: String, projection: ProjectionKey): Int =
        profileDao.setting(capacityKey(trackerId, projection))?.value?.toIntOrNull() ?: 0

    private suspend fun reservePublishedCapacity(batch: ReportBatch, projection: ProjectionKey) {
        val rows = batch.projections.filter { it.key == projection }.flatMap { it.ranges }
            .maxOfOrNull { parseReportRange(it.a1).endRow - 2 } ?: return
        val trackerId = batch.binding.trackerId
        if (rows > publishedCapacity(trackerId, projection)) {
            profileDao.putSetting(SettingEntity(capacityKey(trackerId, projection), rows.toString()))
        }
    }

    override suspend fun journalBeforeDispatch(batch: ReportBatch) = transactions.withWriteTransaction {
        check(current() == batch.binding) { "stale_binding" }
        check(
            reportDao.batches(batch.binding.trackerId)
                .none { it.batchIdentity == batch.batchIdentity }) { "duplicate_batch" }
        // Reserve old table capacity before a possibly ambiguous write so later deletion clears every published row.
        reservePublishedCapacity(batch, ProjectionKey.TimeOff)
        reservePublishedCapacity(batch, ProjectionKey.AttendancePeriods)
        reportDao.putBatch(
            ReportBatchEntity(
                batch.batchIdentity,
                batch.binding.trackerId,
                batch.binding.connectionGeneration,
                json.encodeToString(batch),
                "PREPARED",
                clock.now().toString()
            )
        )
    }

    private suspend fun status(batch: ReportBatch, value: String) {
        val row =
            reportDao.batches(batch.binding.trackerId)
                .find { it.batchIdentity == batch.batchIdentity }
                ?: return
        if (row.connectionGeneration != batch.binding.connectionGeneration || row.payloadJson != json.encodeToString(
                batch
            )
        ) return
        if (row.status == "ACKNOWLEDGED") return
        reportDao.putBatch(row.copy(status = value, updatedAt = clock.now().toString()))
    }

    override suspend fun markDispatched(batch: ReportBatch) = transactions.withWriteTransaction {
        check(current() == batch.binding) { "stale_binding" }; status(batch, "DISPATCHED")
    }

    override suspend fun markAmbiguous(batch: ReportBatch) =
        transactions.withWriteTransaction { status(batch, "AMBIGUOUS") }

    override suspend fun acknowledge(batch: ReportBatch) = transactions.withWriteTransaction {
        if (current() != batch.binding) return@withWriteTransaction
        val journal =
            reportDao.batches(batch.binding.trackerId)
                .find { it.batchIdentity == batch.batchIdentity }
                ?: return@withWriteTransaction
        if (journal.status != "DISPATCHED") return@withWriteTransaction
        status(batch, "ACKNOWLEDGED")
        for (projection in projections(batch.binding)) {
            val dirty = dirtyProjectionDao.dirtyProjection(
                batch.binding.trackerId,
                projection.key.storageKey()
            )
                ?: continue
            val covered = acknowledgedRows(batch.binding, projection)
            if (projection.ranges.flatMap(::individualRows).all { it.a1 in covered })
                dirtyProjectionDao.putDirtyProjection(dirty.copy(acknowledgedRevision = projection.revision))
        }
    }

    override suspend fun markDirty(keys: Set<ProjectionKey>) = transactions.withWriteTransaction {
        val tracker = profileDao.tracker() ?: return@withWriteTransaction
        keys.forEach { key ->
            if (key !is ProjectionKey.Day || key.trackerId == tracker.id) {
                val old = dirtyProjectionDao.dirtyProjection(tracker.id, key.storageKey())
                if (old == null) dirtyProjectionDao.markDirty(
                    tracker.id,
                    key.storageKey()
                ) else dirtyProjectionDao.putDirtyProjection(old.copy(acknowledgedRevision = 0))
            }
        }
        reportDao.batches(tracker.id).filter { it.status == "ACKNOWLEDGED" }.forEach { row ->
            if (json.decodeFromString<ReportBatch>(row.payloadJson).projections.any { it.key in keys }) reportDao.deleteBatch(
                row.batchIdentity
            )
        }
    }

    override suspend fun saveRetry(
        binding: ReportBinding,
        attempts: Int,
        nextAt: Instant?,
        error: String?
    ) = transactions.withWriteTransaction {
        if (current() == binding) profileDao.putSetting(
            SettingEntity(
                retryKey(binding),
                buildJsonObject {
                    put("generation", binding.connectionGeneration); put(
                    "attempts",
                    attempts
                ); put("nextAt", nextAt?.toString()); put("error", error)
                }.toString()
            )
        )
    }

    override suspend fun retryState(binding: ReportBinding): RetryState =
        transactions.withReadTransaction {
            val body =
                profileDao.setting(retryKey(binding))?.value?.let { Json.parseToJsonElement(it).jsonObject }
            if (body?.get("generation")?.jsonPrimitive?.content != binding.connectionGeneration) RetryState()
            else RetryState(
                body.getValue("attempts").jsonPrimitive.int,
                body["nextAt"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse),
                body["error"]?.jsonPrimitive?.contentOrNull
            )
        }
}

internal data class ReportRange(
    val title: String,
    val startRow: Int,
    val endRow: Int,
    val startColumn: String,
    val endColumn: String
)

internal fun parseReportRange(a1: String): ReportRange {
    val match = Regex("^'([^']+)'!([A-Z]+)([1-9][0-9]*):([A-Z]+)([1-9][0-9]*)$").matchEntire(a1)
        ?: throw ReportAccessException("schema_conflict")
    return ReportRange(
        match.groupValues[1],
        match.groupValues[3].toInt(),
        match.groupValues[5].toInt(),
        match.groupValues[2],
        match.groupValues[4]
    ).also {
        require(it.endRow >= it.startRow)
    }
}

internal fun individualRows(range: RawRange): List<RawRange> {
    val parsed = parseReportRange(range.a1)
    require(range.cells.size == parsed.endRow - parsed.startRow + 1)
    return range.cells.mapIndexed { index, row ->
        val at = parsed.startRow + index; RawRange(
        "'${parsed.title}'!${parsed.startColumn}$at:${parsed.endColumn}$at",
        listOf(row)
    )
    }
}
