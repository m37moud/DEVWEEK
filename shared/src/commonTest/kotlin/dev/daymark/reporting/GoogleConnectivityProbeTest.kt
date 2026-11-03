package dev.daymark.reporting

import dev.daymark.auth.GoogleAccess
import dev.daymark.auth.NativeAuthReply
import dev.daymark.auth.NativeAuthStatus
import dev.daymark.auth.NativeGoogleAuth
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleConnectivityProbeTest {
    private val native = object : NativeGoogleAuth {
        override fun authorize(interactive: Boolean, completion: (NativeAuthReply) -> Unit) {
            assertTrue(interactive)
            completion(NativeAuthReply(NativeAuthStatus.GRANTED, "test-only-token", null))
        }

        override fun forget(completion: (Boolean) -> Unit) {
            completion(true)
        }
    }

    @Test
    fun ambiguous_create_stops_and_next_run_discovers_before_creating() = runTest {
        val paths = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when (request.url.encodedPath) {
                "/drive/v3/about" -> respond(
                    """{"user":{"permissionId":"opaque-account"}}""",
                    headers = headers
                )

                "/drive/v3/files" -> if (request.method == HttpMethod.Get) respond(
                    """{"files":[]}""",
                    headers = headers
                )
                else respond("{}", HttpStatusCode.ServiceUnavailable, headers)

                else -> error("Must not write after ambiguous creation")
            }
        }).use { client ->
            val result =
                runGoogleConnectivityProbe(GoogleAccess(native), GoogleReportTransport(client))
            assertEquals(GoogleProbeOutcome.AMBIGUOUS, result)
        }
        assertEquals(listOf("/drive/v3/about", "/drive/v3/files", "/drive/v3/files"), paths)
    }

    @Test
    fun existing_report_is_reused_and_literal_row_is_read_back() = runTest {
        var creates = 0
        HttpClient(MockEngine { request ->
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when (request.url.encodedPath) {
                "/drive/v3/about" -> respond(
                    """{"user":{"permissionId":"opaque-account"}}""",
                    headers = headers
                )

                "/drive/v3/files" -> {
                    if (request.method == HttpMethod.Post) creates++
                    respond("""{"files":[{"id":"probe-report"}]}""", headers = headers)
                }

                "/v4/spreadsheets/probe-report/values:batchUpdate" -> respond(
                    "{}",
                    headers = headers
                )

                "/v4/spreadsheets/probe-report/values:batchGet" -> respond(
                    """{"valueRanges":[{"values":[["Daymark developer test","=1+2","مرحبا"]]}]}""",
                    headers = headers
                )

                else -> error("Unexpected endpoint")
            }
        }).use { client ->
            assertEquals(
                GoogleProbeOutcome.VERIFIED,
                runGoogleConnectivityProbe(GoogleAccess(native), GoogleReportTransport(client))
            )
        }
        assertEquals(0, creates)
    }
}
