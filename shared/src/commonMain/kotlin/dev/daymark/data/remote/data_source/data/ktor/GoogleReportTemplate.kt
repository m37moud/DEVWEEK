package dev.daymark.data.remote.data_source.data.ktor

import dev.daymark.data.local.reporting.overviewHumanColumns
import dev.daymark.data.local.reporting.overviewMachineColumns
import dev.daymark.data.local.reporting.parseReportRange
import dev.daymark.data.local.reporting.reportHumanColumns
import dev.daymark.data.local.reporting.reportMachineColumns
import dev.daymark.data.local.reporting.timeOffHumanColumns
import dev.daymark.data.local.reporting.timeOffMachineColumns
import dev.daymark.models.reporting.RemoteReport
import dev.daymark.models.reporting.ReportAccessException
import dev.daymark.reporting.sameCells
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Stable IDs prevent a renamed tab from redirecting a subsequent write to a different sheet. */
internal fun gridFilter(report: RemoteReport, a1: String): JsonObject {
    val range = parseReportRange(a1)
    val id = report.tabIds[range.title] ?: throw ReportAccessException("schema_conflict")
    fun column(value: String) = value.fold(0) { sum, c -> sum * 26 + (c - 'A' + 1) }
    return buildJsonObject {
        putJsonObject("gridRange") {
            put("sheetId", id); put("startRowIndex", range.startRow - 1); put(
            "endRowIndex",
            range.endRow
        )
            put("startColumnIndex", column(range.startColumn) - 1); put(
            "endColumnIndex",
            column(range.endColumn)
        )
        }
    }
}

private fun headers(title: String): Pair<List<String>, List<String>> = when (title) {
    "Overview" -> overviewMachineColumns to overviewHumanColumns
    "Time off" -> timeOffMachineColumns to timeOffHumanColumns
    else -> reportMachineColumns to reportHumanColumns
}

private data class Sheet(
    val id: Int,
    val title: String,
    val rows: Int,
    val columns: Int,
    val owners: List<String>
)

internal suspend fun GoogleReportTransport.prepareOwnedTemplate(
    report: RemoteReport,
    trackerId: String,
    zoneId: String,
    years: Set<Int>,
    token: String
): RemoteReport = try {
    if (report.schemaVersion != null && report.schemaVersion != 1) throw ReportAccessException("schema_conflict")
    require(report.id.matches(Regex("[A-Za-z0-9_-]+")))
    require(years.all { it in 1..9999 })
    kotlinx.datetime.TimeZone.of(zoneId)
    val response = client.get("https://sheets.googleapis.com/v4/spreadsheets/${report.id}") {
        bearerAuth(token); parameter("fields", "sheets(properties,developerMetadata)")
    }
    val root = with(this) { response.checkedJson() }
    val sheets = root["sheets"]?.jsonArray.orEmpty().map { item ->
        val value = item.jsonObject;
        val properties = value.getValue("properties").jsonObject
        val grid = properties.getValue("gridProperties").jsonObject
        Sheet(
            properties.getValue("sheetId").jsonPrimitive.int,
            properties.getValue("title").jsonPrimitive.content,
            grid.getValue("rowCount").jsonPrimitive.int,
            grid.getValue("columnCount").jsonPrimitive.int,
            value["developerMetadata"]?.jsonArray.orEmpty()
                .filter { it.jsonObject["metadataKey"]?.jsonPrimitive?.content == "daymarkOwner" }
                .map { it.jsonObject.getValue("metadataValue").jsonPrimitive.content })
    }

    fun owner(title: String) = "$trackerId|$title|1"
    val titles = (setOf(
        "Overview",
        "Time off"
    ) + years.map { "Attendance $it" } + report.tabIds.keys).sorted()
    val ids = mutableMapOf<String, Int>()
    val fresh = mutableSetOf<String>()
    val requests = mutableListOf<JsonObject>()
    val used = sheets.map { it.id }.toMutableSet()
    // Validate the whole existing structure before sending any mutation.
    for (title in titles) {
        val expected = report.tabIds[title]
        val named = sheets.filter { it.title == title }
        val owned = sheets.filter { owner(title) in it.owners }
        if (named.size > 1 || owned.size > 1) throw ReportAccessException("schema_conflict")
        val sheet = named.singleOrNull()
        if (sheet != null && (sheet.owners != listOf(owner(title)) || expected?.let { it != sheet.id } == true)) throw ReportAccessException(
            "schema_conflict"
        )
        if (owned.isNotEmpty() && owned.single().title != title) throw ReportAccessException("schema_conflict")
        if (expected != null && sheet == null) throw ReportAccessException("schema_conflict")
        if (sheet == null) {
            var id: Int
            do {
                id = Random.nextInt(1, Int.MAX_VALUE)
            } while (!used.add(id))
            ids[title] = id; fresh += title
            val (machine, human) = headers(title)
            requests += buildJsonObject {
                putJsonObject("addSheet") {
                    putJsonObject("properties") {
                        put("sheetId", id); put("title", title); putJsonObject("gridProperties") {
                        put(
                            "rowCount",
                            maxOf(
                                if (title.startsWith("Attendance ")) 368 else 100,
                                report.requiredRows[title] ?: 0
                            )
                        )
                        put("columnCount", machine.size); put("frozenRowCount", 2)
                    }
                    }
                }
            }
            requests += buildJsonObject {
                putJsonObject("createDeveloperMetadata") {
                    putJsonObject("developerMetadata") {
                        put("metadataKey", "daymarkOwner"); put("metadataValue", owner(title)); put(
                        "visibility",
                        "DOCUMENT"
                    )
                        putJsonObject("location") { put("sheetId", id) }
                    }
                }
            }
            requests += buildJsonObject {
                putJsonObject("updateCells") {
                    putJsonObject("start") {
                        put("sheetId", id); put(
                        "rowIndex",
                        0
                    ); put("columnIndex", 0)
                    }
                    put("fields", "userEnteredValue")
                    putJsonArray("rows") {
                        listOf(machine, human).forEach { row ->
                            addJsonObject {
                                putJsonArray("values") {
                                    row.forEach { text ->
                                        addJsonObject {
                                            putJsonObject("userEnteredValue") {
                                                put(
                                                    "stringValue",
                                                    text
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            ids[title] = sheet.id
            if (sheet.columns < headers(title).first.size || sheet.rows < 2) throw ReportAccessException(
                "schema_conflict"
            )
            val required = maxOf(
                if (title.startsWith("Attendance ")) 368 else 3,
                report.requiredRows[title] ?: 0
            )
            if (required > sheet.rows) requests += buildJsonObject {
                putJsonObject("appendDimension") {
                    put("sheetId", sheet.id); put("dimension", "ROWS"); put(
                    "length",
                    required - sheet.rows
                )
                }
            }
        }
    }
    val ready = report.copy(tabIds = ids, schemaVersion = 1)
    val oldTitles = titles.filter { it !in fresh }
    if (oldTitles.isNotEmpty() && !report.repairHeaders) {
        val ranges =
            oldTitles.map { title -> "'$title'!A1:${('A'.code + headers(title).first.size - 1).toChar()}2" }
        val remote = read(ready, ranges, token)
        oldTitles.zip(ranges).forEach { (title, a1) ->
            val (machine, human) = headers(title)
            val expected = listOf(machine.map(::JsonPrimitive), human.map(::JsonPrimitive))
            if (!sameCells(
                    expected,
                    remote.ranges[a1].orEmpty()
                )
            ) throw ReportAccessException("schema_conflict")
        }
    }
    if (report.repairHeaders) oldTitles.forEach { title ->
        val (machine, human) = headers(title)
        requests += buildJsonObject {
            putJsonObject("updateCells") {
                putJsonObject("start") {
                    put("sheetId", ids.getValue(title)); put(
                    "rowIndex",
                    0
                ); put("columnIndex", 0)
                }
                put("fields", "userEnteredValue")
                putJsonArray("rows") {
                    listOf(machine, human).forEach { row ->
                        addJsonObject {
                            putJsonArray("values") {
                                row.forEach { text ->
                                    addJsonObject {
                                        putJsonObject("userEnteredValue") {
                                            put(
                                                "stringValue",
                                                text
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (requests.isNotEmpty()) {
        val updated =
            client.post("https://sheets.googleapis.com/v4/spreadsheets/${report.id}:batchUpdate") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("requests", JsonArray(requests)) }.toString())
            }
        with(this) { updated.checkedJson() }
    }
    ready
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (known: ReportAccessException) {
    throw known
} catch (_: Exception) {
    throw ReportAccessException("report_read_failed")
}
