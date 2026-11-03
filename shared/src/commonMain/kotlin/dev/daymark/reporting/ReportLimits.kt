package dev.daymark.reporting

import kotlinx.serialization.json.Json

/** Conservative per-field boundary; count UTF-16 units so EN/AR and surrogate pairs stay within it. */
const val REPORT_TEXT_LIMIT = 50_000
fun exceedsReportTextLimit(value: String?): Boolean =
    value != null && value.length > REPORT_TEXT_LIMIT

enum class ReportField { NOTE, PROFILE_NAME, RECORD }
data class ReportIssue(val key: ProjectionKey, val field: ReportField)

internal fun reportIssue(key: ProjectionKey, row: RawRange): ReportIssue? {
    val values = row.cells.single()
    val tooLong = values.indexOfFirst { it.isString && exceedsReportTextLimit(it.content) }
    if (tooLong < 0 && Json.encodeToString(RawRange.serializer(), row)
            .encodeToByteArray().size + 512 <= 1_900_000
    ) return null
    val field = when {
        key is ProjectionKey.Day && tooLong == 9 -> ReportField.NOTE
        key == ProjectionKey.Overview && values.firstOrNull()?.content == "name" && tooLong == 2 -> ReportField.PROFILE_NAME
        else -> ReportField.RECORD
    }
    return ReportIssue(key, field)
}
