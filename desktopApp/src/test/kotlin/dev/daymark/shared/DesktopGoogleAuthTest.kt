package dev.daymark.shared

import dev.daymark.auth.NativeAuthReply
import dev.daymark.auth.NativeAuthStatus
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class DesktopGoogleAuthTest {
    private val client = DesktopOAuthClient("test.apps.googleusercontent.com", "test-client-secret")

    @Test
    fun downloaded_client_secret_is_used_only_for_token_requests(): Unit = runBlocking {
        val folder = Files.createTempDirectory("daymark-oauth-client-secret-")
        val file = folder.resolve("client.json")
        try {
            Files.writeString(
                file,
                """{"installed":{"client_id":"test.apps.googleusercontent.com","client_secret":"test-client-secret"}}"""
            )
            val configuredClient = requireNotNull(loadDesktopOAuthClient(file))
            val parameters = parameters(
                authorizationUri(
                    configuredClient,
                    "http://127.0.0.1:1234/oauth2callback", "verifier", "nonce"
                )
            )
            assertFalse(parameters.containsKey("client_secret"))
            val flow = DesktopOAuthFlow(tokenRequest = { request ->
                assertEquals("test-client-secret", request["client_secret"])
                Json.parseToJsonElement("{}").jsonObject
            })
            flow.refresh(configuredClient, "test-refresh")
        } finally {
            file.toFile().delete()
            folder.toFile().delete()
        }
    }

    @Test
    fun local_client_file_accepts_only_valid_installed_app_configuration() {
        val folder = Files.createTempDirectory("daymark-oauth-client-")
        val file = folder.resolve("client.json")
        try {
            assertNull(loadDesktopOAuthClient(file))
            for (invalid in listOf(
                "{}", "{", """{"web":{"client_id":"test.apps.googleusercontent.com"}}""",
                """{"installed":{"client_id":"invalid"}}""", " ".repeat(65537)
            )) {
                Files.writeString(file, invalid)
                assertNull(loadDesktopOAuthClient(file))
            }
            Files.writeString(
                file,
                """{"installed":{"client_id":"test.apps.googleusercontent.com","client_secret":"ignored"}}"""
            )
            assertEquals(client.id, loadDesktopOAuthClient(file)?.id)
        } finally {
            file.toFile().delete()
            folder.toFile().delete()
        }
    }

    @Test
    fun authorization_uses_pkce_and_only_drive_file_scope() {
        val uri = authorizationUri(
            client, "http://127.0.0.1:1234/oauth2callback",
            "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", "nonce"
        )
        val parameters = parameters(uri)
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", parameters["code_challenge"])
        assertEquals("S256", parameters["code_challenge_method"])
        assertEquals("https://www.googleapis.com/auth/drive.file", parameters["scope"])
        assertFalse(parameters.containsKey("client_secret"))
    }

    @Test
    fun callback_rejects_forged_duplicate_empty_and_malformed_fields() {
        listOf(
            null,
            "state=wrong&code=x",
            "state=n&state=n&code=x",
            "state=n&code=",
            "state=n&code=x&error=access_denied",
            "state=n&code=%ZZ",
            "state=n&code=" + "x".repeat(8192)
        )
            .forEach { assertNull(oauthCallback(it, "n")) }
        assertEquals("a+b", oauthCallback("state=n&code=a%2Bb", "n")?.get("code"))
        assertEquals(
            "access_denied",
            oauthCallback("state=n&error=access_denied", "n")?.get("error")
        )
    }

    @Test
    fun browser_callback_exchanges_code_and_restores_session_without_browser() = runBlocking {
        val store = MemoryGoogleSession()
        var browsed = 0
        val grants = mutableListOf<String>()
        val flow = DesktopOAuthFlow(browse = { uri ->
            browsed++
            val request = parameters(uri)
            val callback = URI(
                request.getValue("redirect_uri") + "?state=" +
                        URLEncoder.encode(request.getValue("state"), Charsets.UTF_8) + "&code=code"
            )
            HttpClient.newHttpClient().use { http ->
                assertEquals(
                    200, http.send(
                        HttpRequest.newBuilder(callback).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                    ).statusCode()
                )
            }
        }, tokenRequest = { request ->
            grants += request.getValue("grant_type")
            assertEquals("test-client-secret", request["client_secret"])
            if (request["grant_type"] == "authorization_code") {
                assertEquals("code", request["code"])
                assertEquals(43, request.getValue("code_verifier").length)
            } else assertEquals("refresh", request["refresh_token"])
            Json.parseToJsonElement("""{"access_token":"access","token_type":"Bearer","refresh_token":"refresh"}""").jsonObject
        })
        val auth = DesktopGoogleAuth({ client }, store, flow)
        try {
            assertEquals(NativeAuthStatus.GRANTED, reply(auth, true).status)
            assertFalse(store.read()!!.contains("access_token"))
            assertEquals(NativeAuthStatus.GRANTED, reply(auth, false).status)
            assertEquals(1, browsed)
            assertEquals(listOf("authorization_code", "refresh_token"), grants)
            val forgotten = CompletableDeferred<Boolean>()
            auth.forget { forgotten.complete(it) }
            assertTrue(withTimeout(5000) { forgotten.await() })
            assertNull(store.read())
            assertEquals(NativeAuthStatus.NEEDS_INTERACTION, reply(auth, false).status)
        } finally {
            auth.close()
        }
    }

    @Test
    fun missing_client_reports_configuration_failure_without_browser() = runBlocking {
        val auth = DesktopGoogleAuth(
            { null },
            MemoryGoogleSession(),
            DesktopOAuthFlow(browse = { error("No browser") })
        )
        try {
            val reply = reply(auth, true)
            assertEquals(NativeAuthStatus.FAILED, reply.status)
            assertEquals("authorization_configuration", reply.errorKey)
        } finally {
            auth.close()
        }
    }

    @Test
    fun disconnect_during_token_exchange_cannot_restore_credentials() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val store = MemoryGoogleSession().apply {
            write("""{"client_id":"test.apps.googleusercontent.com","refresh_token":"refresh"}""")
        }
        val flow =
            DesktopOAuthFlow(browse = { error("Silent refresh cannot browse") }, tokenRequest = {
                started.complete(Unit)
                CompletableDeferred<kotlinx.serialization.json.JsonObject>().await()
            })
        val auth = DesktopGoogleAuth({ client }, store, flow)
        try {
            val reply = CompletableDeferred<NativeAuthReply>()
            auth.authorize(false) { reply.complete(it) }
            withTimeout(5000) { started.await() }
            val forgotten = CompletableDeferred<Boolean>()
            auth.forget { forgotten.complete(it) }
            assertTrue(withTimeout(5000) { forgotten.await() })
            assertEquals(
                NativeAuthStatus.NEEDS_INTERACTION,
                withTimeout(5000) { reply.await() }.status
            )
            assertNull(store.read())
        } finally {
            auth.close()
        }
    }

    private suspend fun reply(auth: DesktopGoogleAuth, interactive: Boolean): NativeAuthReply {
        val completed = CompletableDeferred<NativeAuthReply>()
        auth.authorize(interactive) { completed.complete(it) }
        return withTimeout(5000) { completed.await() }
    }

    private fun parameters(uri: URI) = uri.rawQuery.split('&').associate {
        val pair = it.split('=', limit = 2)
        URLDecoder.decode(pair[0], Charsets.UTF_8) to URLDecoder.decode(pair[1], Charsets.UTF_8)
    }
}
