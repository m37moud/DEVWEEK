package dev.daymark.reporting

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class GoogleReportTransportTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    @Test fun literal_write_and_account_identity() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/drive/v3/about" -> {
                    assertEquals("user(permissionId)", request.url.parameters["fields"])
                    val account = if (request.headers[HttpHeaders.Authorization] == "Bearer account-a-token") "opaque-a" else "opaque-b"
                    respond("""{"user":{"permissionId":"$account"}}""", headers=jsonHeaders)
                }
                "/v4/spreadsheets/report-1/values:batchUpdate" -> {
                    val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    assertEquals("RAW", body.getValue("valueInputOption").jsonPrimitive.content)
                    val row=body.getValue("data").jsonArray[0].jsonObject.getValue("values").jsonArray[0].jsonArray
                    assertEquals("=1+2", row[0].jsonPrimitive.content)
                    assertEquals("مرحبا", row[1].jsonPrimitive.content)
                    respond("{}", headers=jsonHeaders)
                }
                else -> error("Unexpected endpoint")
            }
        }
        HttpClient(engine).use { client ->
            val transport=GoogleReportTransport(client)
            assertNotEquals(transport.accountReference("account-a-token"),transport.accountReference("account-b-token"))
            assertIs<RemoteWriteOutcome.Acknowledged>(transport.write(RemoteReport("report-1",emptyMap(),1),
                RawPatch(listOf(RawRange("'2026'!A3:B3",listOf(listOf(JsonPrimitive("=1+2"),JsonPrimitive("مرحبا")))))),"account-a-token"))
        }
    }
    @Test fun unauthorized_does_not_retry_or_acknowledge() = runTest {
        var count=0
        HttpClient(MockEngine { count++; respond("{}",HttpStatusCode.Unauthorized,jsonHeaders) }).use { client ->
            assertIs<RemoteWriteOutcome.AuthorizationRequired>(GoogleReportTransport(client).write(
                RemoteReport("r",emptyMap(),1), RawPatch(listOf(RawRange("A1",listOf(listOf(JsonPrimitive("text")))))),"token"))
        }
        assertEquals(1,count)
    }
    @Test fun failed_create_is_ambiguous_and_not_retried() = runTest {
        var count=0
        HttpClient(MockEngine { count++; respond("{}",HttpStatusCode.ServiceUnavailable,jsonHeaders) }).use { client ->
            assertIs<ReportCreation.Ambiguous>(GoogleReportTransport(client).create("Daymark developer test","tracker-1","token"))
        }
        assertEquals(1,count)
    }
    @Test fun discovery_escapes_tracker_id_and_reports_multiple_matches() = runTest {
        HttpClient(MockEngine { request ->
            assertEquals("100",request.url.parameters["pageSize"])
            assertTrue(request.url.parameters["q"]!!.contains("a\\'b"))
            respond("""{"files":[{"id":"r1"},{"id":"r2"}]}""",headers=jsonHeaders)
        }).use { client ->
            val found=assertIs<ReportDiscovery.Multiple>(GoogleReportTransport(client).discover("a'b","token"))
            assertEquals(listOf("r1","r2"),found.reports.map { it.id })
        }
    }
}
