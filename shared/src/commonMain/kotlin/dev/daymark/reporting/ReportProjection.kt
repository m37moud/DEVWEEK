package dev.daymark.reporting

import dev.daymark.attendance.AttendanceStatus
import dev.daymark.attendance.HistoryRecord
import dev.daymark.plan.TimeOffEntry
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

val reportMachineColumns = listOf(
    "trackerId",
    "date",
    "category",
    "scheduledArrival",
    "graceDeadline",
    "status",
    "statusLabel",
    "confirmedArrival",
    "source",
    "note",
    "updatedAt",
    "revision",
    "offReason",
    "attendanceZoneId",
    "confirmedAtUtc"
)
val reportHumanColumns = listOf(
    "Tracker / المتابع",
    "Date / التاريخ",
    "Category / النوع",
    "Arrival / الحضور",
    "Deadline / المهلة",
    "Status / الحالة",
    "Status label / وصف الحالة",
    "Confirmed arrival / وقت التأكيد",
    "Source / المصدر",
    "Note / ملاحظة",
    "Updated / التحديث",
    "Revision / الإصدار",
    "Time off / الإجازة",
    "Time zone / المنطقة الزمنية",
    "Confirmed UTC / التأكيد العالمي"
)
internal val timeOffMachineColumns = listOf("id", "kind", "from", "through", "note", "updatedAt")
internal val timeOffHumanColumns = listOf(
    "ID / المعرف",
    "Kind / النوع",
    "From / من",
    "Through / إلى",
    "Note / ملاحظة",
    "Updated / التحديث"
)
internal val overviewMachineColumns = listOf("field", "label", "value")
internal val overviewHumanColumns = listOf("Field / الحقل", "Label / الوصف", "Value / القيمة")
internal fun bilingualStatus(status: AttendanceStatus): String =
    status.name + " / " + when (status) {
        AttendanceStatus.SCHEDULED -> "مجدول"
        AttendanceStatus.AWAITING_CHECK -> "بانتظار التأكيد"
        AttendanceStatus.PRESENT -> "حاضر"
        AttendanceStatus.LATE -> "متأخر"
        AttendanceStatus.ABSENT -> "غائب"
        AttendanceStatus.DAY_OFF -> "يوم راحة"
        AttendanceStatus.HOLIDAY -> "عطلة"
        AttendanceStatus.VACATION -> "إجازة"
    }

fun projectDay(record: HistoryRecord, revision: Long, updatedAt: Instant): ReportProjection {
    val plan = record.plan
    val outcome = record.outcome
    val zone = TimeZone.of(plan.zoneId)
    fun time(at: Instant?) = at?.toLocalDateTime(zone)?.time?.toString().orEmpty()
    val row = listOf(
        JsonPrimitive(plan.key.trackerId),
        JsonPrimitive(plan.key.date.toString()),
        JsonPrimitive(plan.category.name),
        JsonPrimitive(time(plan.arrival)),
        JsonPrimitive(time(plan.deadline)),
        JsonPrimitive(outcome.status.name),
        JsonPrimitive(bilingualStatus(outcome.status)),
        JsonPrimitive(time(outcome.confirmedAt)),
        JsonPrimitive(outcome.source.name),
        JsonPrimitive(record.evidence?.note.orEmpty()),
        JsonPrimitive(updatedAt.toString()),
        JsonPrimitive(revision),
        JsonPrimitive(outcome.offReason?.name.orEmpty()),
        JsonPrimitive(plan.zoneId),
        JsonPrimitive(outcome.confirmedAt?.toString().orEmpty())
    )
    val rowNumber = plan.key.date.dayOfYear + 2
    return ReportProjection(
        ProjectionKey.Day(plan.key),
        revision,
        listOf(RawRange("'Attendance ${plan.key.date.year}'!A$rowNumber:O$rowNumber", listOf(row)))
    )
}

fun projectTimeOff(
    entries: List<TimeOffEntry>,
    revision: Long,
    previousCapacity: Int
): ReportProjection {
    val sorted = entries.sortedWith(compareBy<TimeOffEntry> { it.from }.thenBy { it.id })
    val capacity = maxOf(1, previousCapacity, entries.size)
    val rows = List(capacity) { index ->
        sorted.getOrNull(index)?.let {
            listOf(
                it.id,
                it.kind.name,
                it.from.toString(),
                it.through.toString(),
                "",
                it.updatedAt.toString()
            ).map(::JsonPrimitive)
        } ?: List(6) { JsonPrimitive("") }
    }
    return ReportProjection(
        ProjectionKey.TimeOff,
        revision,
        listOf(RawRange("'Time off'!A3:F${capacity + 2}", rows))
    )
}

internal fun projectOverview(values: List<Triple<String, String, JsonPrimitive>>, revision: Long) =
    ReportProjection(
        ProjectionKey.Overview,
        revision,
        listOf(RawRange("'Overview'!A3:C${values.size + 2}", values.map { (key, label, value) ->
            listOf(JsonPrimitive(key), JsonPrimitive(label), value)
        }))
    )
