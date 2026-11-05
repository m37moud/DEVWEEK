package dev.daymark.reporting

import dev.daymark.data.remote.data_source.data.ktor.GoogleReportTransport
import dev.daymark.models.reporting.RawPatch
import dev.daymark.models.reporting.RawRange
import dev.daymark.models.reporting.RemoteReport
import dev.daymark.models.reporting.RemoteWriteOutcome
import dev.daymark.models.reporting.ReportAccessException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class ReportTemplateTest {
    @Test
    fun managed_writes_use_stable_sheet_ids_and_raw_values() = runTest {
        HttpClient(MockEngine { request ->
            assertTrue(request.url.encodedPath.endsWith("values:batchUpdateByDataFilter"))
            val body =
                Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            assertEquals("RAW", body.getValue("valueInputOption").jsonPrimitive.content)
            val data = body.getValue("data").jsonArray.single().jsonObject
            val grid = data.getValue("dataFilter").jsonObject.getValue("gridRange").jsonObject
            assertEquals(42, grid.getValue("sheetId").jsonPrimitive.int)
            assertEquals(61, grid.getValue("startRowIndex").jsonPrimitive.int)
            assertEquals(
                "=1+2",
                data.getValue("values").jsonArray.single().jsonArray[9].jsonPrimitive.content
            )
            respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { client ->
            val report = RemoteReport("r", mapOf("Attendance 2028" to 42), 1)
            val row = List(15) { JsonPrimitive(if (it == 9) "=1+2" else "") }
            assertEquals(
                RemoteWriteOutcome.Acknowledged,
                GoogleReportTransport(client).write(
                    report,
                    RawPatch(listOf(RawRange("'Attendance 2028'!A62:O62", listOf(row)))),
                    "token"
                )
            )
        }
    }

    @Test
    fun unrelated_same_title_tab_is_never_adopted_or_written() = runTest {
        var mutations = 0
        HttpClient(MockEngine { request ->
            if (request.method != HttpMethod.Get) mutations++
            respond(
                """{"sheets":[{"properties":{"sheetId":4,"title":"Overview","gridProperties":{"rowCount":100,"columnCount":26}}}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }).use { client ->
            val error = assertFailsWith<ReportAccessException> {
                GoogleReportTransport(client).prepareTemplate(
                    RemoteReport("r", emptyMap(), null),
                    "tracker",
                    "UTC",
                    setOf(2026),
                    "token"
                )
            }
            assertEquals("schema_conflict", error.errorKey)
        }
        assertEquals(0, mutations)
    }

    @Test
    fun stable_id_reads_match_filters_instead_of_response_order() = runTest {
        HttpClient(MockEngine { request ->
            assertTrue(request.url.encodedPath.endsWith("values:batchGetByDataFilter"))
            val body =
                Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            val filters = body.getValue("dataFilters").jsonArray
            val response = buildJsonObject {
                putJsonArray("valueRanges") {
                    filters.reversed().forEach { filter ->
                        addJsonObject {
                            put("dataFilters", JsonArray(listOf(filter)))
                            putJsonObject("valueRange") {
                                put(
                                    "values",
                                    JsonArray(
                                        listOf(
                                            JsonArray(
                                                listOf(
                                                    JsonPrimitive(
                                                        filter.jsonObject.getValue("gridRange").jsonObject.getValue(
                                                            "startRowIndex"
                                                        ).jsonPrimitive.int
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
            respond(
                response.toString(),
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }).use { client ->
            val ranges = listOf("'Overview'!A3:C3", "'Overview'!A4:C4")
            val result = GoogleReportTransport(client).read(
                RemoteReport("r", mapOf("Overview" to 11), 1),
                ranges,
                "token"
            )
            assertEquals(JsonPrimitive(2), result.ranges.getValue(ranges[0]).single().single())
            assertEquals(JsonPrimitive(3), result.ranges.getValue(ranges[1]).single().single())
        }
    }

    @Test
    fun template_creation_preserves_unrelated_sheet_and_atomically_tags_and_labels_owned_tabs() =
        runTest {
            var mutationCount = 0
            HttpClient(MockEngine { request ->
                if (request.method == HttpMethod.Get) respond(
                    """{"sheets":[{"properties":{"sheetId":0,"title":"Personal notes","gridProperties":{"rowCount":100,"columnCount":26}}}]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else {
                    mutationCount++
                    val operations = Json.parseToJsonElement(
                        request.body.toByteArray().decodeToString()
                    ).jsonObject.getValue("requests").jsonArray
                    assertEquals(3, operations.count { "addSheet" in it.jsonObject })
                    assertEquals(3, operations.count { "createDeveloperMetadata" in it.jsonObject })
                    assertEquals(3, operations.count { "updateCells" in it.jsonObject })
                    assertFalse(operations.any { "deleteSheet" in it.jsonObject })
                    assertFalse(operations.toString().contains("\"sheetId\":0"))
                    respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }).use { client ->
                val result = GoogleReportTransport(client).prepareTemplate(
                    RemoteReport(
                        "r",
                        emptyMap(),
                        null
                    ), "tracker", "UTC", setOf(2028), "token"
                )
                assertEquals(setOf("Overview", "Time off", "Attendance 2028"), result.tabIds.keys)
                assertEquals(1, result.schemaVersion)
            }
            assertEquals(1, mutationCount)
        }

}
