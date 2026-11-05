package dev.daymark.reporting

import dev.daymark.data.remote.data_source.data.ktor.GoogleReportTransport
import dev.daymark.models.reporting.RawPatch
import dev.daymark.models.reporting.RawRange
import dev.daymark.models.reporting.RemoteReport
import dev.daymark.models.reporting.RemoteWriteOutcome
import dev.daymark.models.reporting.ReportCreation
import dev.daymark.models.reporting.ReportDiscovery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.toHttpDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GoogleReportTransportTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    @Test
    fun literal_write_and_account_identity() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/drive/v3/about" -> {
                    assertEquals("user(permissionId)", request.url.parameters["fields"])
                    val account =
                        if (request.headers[HttpHeaders.Authorization] == "Bearer account-a-token") "opaque-a" else "opaque-b"
                    respond("""{"user":{"permissionId":"$account"}}""", headers = jsonHeaders)
                }

                "/v4/spreadsheets/report-1/values:batchUpdate" -> {
                    val body = Json.parseToJsonElement(
                        request.body.toByteArray().decodeToString()
                    ).jsonObject
                    assertEquals("RAW", body.getValue("valueInputOption").jsonPrimitive.content)
                    val row =
                        body.getValue("data").jsonArray[0].jsonObject.getValue("values").jsonArray[0].jsonArray
                    assertEquals("=1+2", row[0].jsonPrimitive.content)
                    assertEquals("مرحبا", row[1].jsonPrimitive.content)
                    respond("{}", headers = jsonHeaders)
                }

                else -> error("Unexpected endpoint")
            }
        }
        HttpClient(engine).use { client ->
            val transport = GoogleReportTransport(client)
            assertNotEquals(
                transport.accountReference("account-a-token"),
                transport.accountReference("account-b-token")
            )
            assertIs<RemoteWriteOutcome.Acknowledged>(
                transport.write(
                    RemoteReport("report-1", emptyMap(), 1),
                    RawPatch(
                        listOf(
                            RawRange(
                                "'2026'!A3:B3",
                                listOf(listOf(JsonPrimitive("=1+2"), JsonPrimitive("مرحبا")))
                            )
                        )
                    ), "account-a-token"
                )
            )
        }
    }

    @Test
    fun unauthorized_does_not_retry_or_acknowledge() = runTest {
        var count = 0
        HttpClient(MockEngine {
            count++; respond(
            "{}",
            HttpStatusCode.Unauthorized,
            jsonHeaders
        )
        }).use { client ->
            assertIs<RemoteWriteOutcome.AuthorizationRequired>(
                GoogleReportTransport(client).write(
                    RemoteReport("r", emptyMap(), 1),
                    RawPatch(listOf(RawRange("A1", listOf(listOf(JsonPrimitive("text")))))),
                    "token"
                )
            )
        }
        assertEquals(1, count)
    }

    @Test
    fun failed_create_is_ambiguous_and_not_retried() = runTest {
        var count = 0
        HttpClient(MockEngine {
            count++; respond(
            "{}",
            HttpStatusCode.ServiceUnavailable,
            jsonHeaders
        )
        }).use { client ->
            assertIs<ReportCreation.Ambiguous>(
                GoogleReportTransport(client).create(
                    "Daymark developer test",
                    "tracker-1",
                    "token"
                )
            )
        }
        assertEquals(1, count)
    }

    @Test
    fun discovery_escapes_tracker_id_and_reports_multiple_matches() = runTest {
        HttpClient(MockEngine { request ->
            assertEquals("100", request.url.parameters["pageSize"])
            assertTrue(request.url.parameters["q"]!!.contains("a\\'b"))
            respond("""{"files":[{"id":"r1"},{"id":"r2"}]}""", headers = jsonHeaders)
        }).use { client ->
            val found = assertIs<ReportDiscovery.Multiple>(
                GoogleReportTransport(client).discover(
                    "a'b",
                    "token"
                )
            )
            assertEquals(listOf("r1", "r2"), found.reports.map { it.id })
        }
    }

    @Test
    fun retry_after_http_date_is_honored() = runTest {
        val future = kotlin.time.Clock.System.now() + kotlin.time.Duration.parse("10m")
        val date = io.ktor.util.date.GMTDate(future.toEpochMilliseconds()).toHttpDate()
        HttpClient(MockEngine {
            respond(
                "{}",
                HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.RetryAfter, date)
            )
        }).use { client ->
            val result = assertIs<RemoteWriteOutcome.Retryable>(
                GoogleReportTransport(client).write(
                    RemoteReport("r", emptyMap(), 1),
                    RawPatch(listOf(RawRange("A1", listOf(listOf(JsonPrimitive("x")))))), "token"
                )
            )
            assertTrue(result.retryAfter != null && result.retryAfter > kotlin.time.Duration.parse("9m"))
        }
    }

    @Test
    fun another_accounts_shared_report_is_not_adopted_during_discovery() = runTest {
        HttpClient(MockEngine { request ->
            val ownerOnly = request.url.parameters["q"]?.contains("'me' in owners") == true
            // The same tracker report is shared from A to B; B must discover only B-owned reports.
            val files = if (ownerOnly) "[]" else "[{\"id\":\"account-a-shared-report\"}]"
            respond("{\"files\":$files}", headers = jsonHeaders)
        }).use { client ->
            assertEquals(
                ReportDiscovery.None,
                GoogleReportTransport(client).discover("personal-tracker", "account-b-token")
            )
        }
    }

}
