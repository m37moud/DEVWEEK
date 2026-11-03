package dev.daymark.reporting

import dev.daymark.attendance.TestClock
import dev.daymark.auth.GoogleAccess
import dev.daymark.auth.NativeAuthReply
import dev.daymark.auth.NativeAuthStatus
import dev.daymark.auth.NativeGoogleAuth
import dev.daymark.data.openJvmDatabase
import dev.daymark.data.seedDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionServiceTest {
    private class Native : NativeGoogleAuth {
        var account = "a"
        val interactive = mutableListOf<Boolean>()
        override fun authorize(interactive: Boolean, completion: (NativeAuthReply) -> Unit) {
            this.interactive += interactive; completion(
                NativeAuthReply(
                    NativeAuthStatus.GRANTED,
                    account,
                    null
                )
            )
        }

        override fun forget(completion: (Boolean) -> Unit) {
            completion(true)
        }
    }

    @Test
    fun account_switch_preserves_previous_report(): Unit = runBlocking {
        val folder = Files.createTempDirectory("daymark-connection-");
        val db = openJvmDatabase(folder.resolve("test.db").toString())
        val methods = mutableListOf<HttpMethod>()
        HttpClient(MockEngine { request ->
            methods += request.method
            val account = request.headers[HttpHeaders.Authorization]!!.removePrefix("Bearer ")
            val response = when {
                request.url.encodedPath.endsWith("/about") -> """{"user":{"permissionId":"$account"}}"""
                request.url.encodedPath.endsWith("/files") -> """{"files":[{"id":"report-$account","appProperties":{"daymarkSchemaVersion":"1"}}]}"""
                else -> error("No remote write/delete during connect")
            }
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { client ->
            try {
                seedDatabase(db);
                val native = Native();
                val store = RoomReportStore(db, TestClock())
                val service = ConnectionService(
                    db,
                    GoogleAccess(native),
                    GoogleReportTransport(client),
                    store
                )
                val a = assertIs<ConnectionResult.Connected>(service.connect()).binding
                native.account = "b"
                val b = assertIs<ConnectionResult.Connected>(service.reconnect()).binding
                assertNotEquals(a.spreadsheetId, b.spreadsheetId)
                assertNotEquals(a.connectionGeneration, b.connectionGeneration)
                assertEquals("b", store.activeBinding()!!.accountReference)
                service.disconnect()
                assertNull(store.activeBinding())
                assertTrue(native.interactive.all { it })
                assertTrue(methods.all { it == HttpMethod.Get })
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun ambiguous_creation_is_searched_on_reopen_and_never_blindly_repeated(): Unit = runBlocking {
        val folder = Files.createTempDirectory("daymark-create-");
        var db = openJvmDatabase(folder.resolve("test.db").toString())
        var creates = 0
        HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/about") -> respond(
                    """{"user":{"permissionId":"a"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                request.method == HttpMethod.Post -> {
                    creates++; respond("{}", HttpStatusCode.ServiceUnavailable)
                }

                else -> respond(
                    """{"files":[]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }).use { client ->
            try {
                seedDatabase(db)
                var service = ConnectionService(
                    db,
                    GoogleAccess(Native()),
                    GoogleReportTransport(client),
                    RoomReportStore(db, TestClock())
                )
                assertIs<ConnectionResult.PendingCreation>(service.connect())
                db.close(); db = openJvmDatabase(folder.resolve("test.db").toString())
                service = ConnectionService(
                    db,
                    GoogleAccess(Native()),
                    GoogleReportTransport(client),
                    RoomReportStore(db, TestClock())
                )
                assertIs<ConnectionResult.PendingCreation>(service.connect())
                assertEquals(1, creates)
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun disconnect_during_consent_fences_late_callback_without_api_calls(): Unit =
        kotlinx.coroutines.runBlocking {
            val folder = Files.createTempDirectory("daymark-connect-fence-");
            val db = openJvmDatabase(folder.resolve("test.db").toString())
            val requested = kotlinx.coroutines.CompletableDeferred<Unit>()
            var callback: ((NativeAuthReply) -> Unit)? = null
            var apiCalls = 0
            val native = object : NativeGoogleAuth {
                override fun authorize(
                    interactive: Boolean,
                    completion: (NativeAuthReply) -> Unit
                ) {
                    callback = completion; requested.complete(Unit)
                }

                override fun forget(completion: (Boolean) -> Unit) {
                    completion(true)
                }
            }
            HttpClient(MockEngine { apiCalls++; error("No API call after disconnected consent") }).use { client ->
                try {
                    seedDatabase(db);
                    val store = RoomReportStore(db, TestClock())
                    val service = ConnectionService(
                        db,
                        GoogleAccess(native),
                        GoogleReportTransport(client),
                        store
                    )
                    val pending = async { service.connect() }
                    requested.await(); service.disconnect()
                    callback!!(NativeAuthReply(NativeAuthStatus.GRANTED, "old-account-token", null))
                    assertFalse(pending.await() is ConnectionResult.Connected)
                    assertNull(store.activeBinding()); assertEquals(0, apiCalls)
                } finally {
                    db.close(); folder.toFile().deleteRecursively()
                }
            }
        }

}
