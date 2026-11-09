package dev.daymark.shared

import com.sun.net.httpserver.HttpServer
import dev.daymark.auth.GOOGLE_REPORT_SCOPES
import dev.daymark.resources.Res
import dev.daymark.resources.report_browser_complete
import dev.daymark.resources.report_browser_invalid
import org.jetbrains.compose.resources.getString
import java.awt.Desktop
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopOAuthFlow(
    private val browse: (URI) -> Unit = { Desktop.getDesktop().browse(it) },
    private val tokenRequest: suspend (Map<String, String>) -> JsonObject = ::googleToken
) {

    suspend fun authorize(client: DesktopOAuthClient): JsonObject? {
        val verifier = randomOAuthValue()
        val state = randomOAuthValue()
        val reply = CompletableDeferred<Map<String, String>>()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        val redirect = "http://127.0.0.1:${server.address.port}/oauth2callback"
        try {
            registerCallback(server, state, reply)
            server.start()
            browse(authorizationUri(client, redirect, verifier, state))
            val parameters = withTimeout(180_000) { reply.await() }
            if (parameters["error"] == "access_denied") return null
            check(parameters["error"] == null) { "authorization_failed" }
            return tokenRequest(
                client.tokenParameters() + mapOf(
                    "code" to parameters.getValue("code"),
                    "code_verifier" to verifier,
                    "redirect_uri" to redirect,
                    "grant_type" to "authorization_code"
                )
            )
        } finally {
            server.stop(0)
            reply.cancel()
        }
    }

    private suspend fun registerCallback(
        server: HttpServer,
        state: String,
        reply: CompletableDeferred<Map<String, String>>
    ) {
        val claimed = AtomicBoolean()
        val completeMessage = getString(Res.string.report_browser_complete)
        val invalidMessage = getString(Res.string.report_browser_invalid)
        server.createContext("/oauth2callback") { exchange ->
            val parameters =
                if (exchange.requestMethod == "GET" && exchange.requestURI.path == "/oauth2callback")
                    oauthCallback(exchange.requestURI.rawQuery, state) else null
            val accepted = parameters != null && claimed.compareAndSet(false, true)
            val message = if (accepted) completeMessage else invalidMessage
            val bytes = message.toByteArray(UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            exchange.responseHeaders.set("Cache-Control", "no-store")
            exchange.sendResponseHeaders(if (accepted) 200 else 400, bytes.size.toLong())
            try {
                exchange.responseBody.use { it.write(bytes) }
            } finally {
                if (accepted) reply.complete(parameters)
            }
        }
    }

    suspend fun refresh(client: DesktopOAuthClient, refreshToken: String) = tokenRequest(
        client.tokenParameters() + mapOf(
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token"
        )
    )

}

private fun DesktopOAuthClient.tokenParameters(): Map<String, String> = buildMap {
    put("client_id", id)
    clientSecret?.let { put("client_secret", it) }
}

private suspend fun googleToken(parameters: Map<String, String>): JsonObject =
    runInterruptible(Dispatchers.IO) {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build().use { http ->
            val request = HttpRequest.newBuilder(URI("https://oauth2.googleapis.com/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(oauthForm(parameters))).build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == 200) { "authorization_failed" }
            Json.parseToJsonElement(response.body()).jsonObject
        }
    }

internal fun authorizationUri(
    client: DesktopOAuthClient,
    redirect: String,
    verifier: String,
    state: String
): URI =
    URI(
        "https://accounts.google.com/o/oauth2/v2/auth?" + oauthForm(
            mapOf(
                "client_id" to client.id,
                "redirect_uri" to redirect,
                "response_type" to "code",
                "scope" to GOOGLE_REPORT_SCOPES.joinToString(" "),
                "code_challenge" to Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(UTF_8))
                    ),
                "code_challenge_method" to "S256",
                "state" to state,
                "access_type" to "offline",
                "prompt" to "consent select_account"
            )
        )
    )

internal fun oauthCallback(query: String?, expectedState: String): Map<String, String>? {
    // Reject missing, duplicate, malformed, oversized, and mismatched callback fields.
    if (query == null || query.length > 8192) return null
    val entries = try {
        query.split('&').map { field ->
            val pair = field.split('=', limit = 2)
            if (pair.size != 2) return null
            URLDecoder.decode(pair[0], UTF_8) to URLDecoder.decode(pair[1], UTF_8)
        }
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (entries.map { it.first }.distinct().size != entries.size) return null
    val parameters = entries.toMap()
    if (parameters["state"] != expectedState) return null
    val hasCode = !parameters["code"].isNullOrBlank()
    val hasError = !parameters["error"].isNullOrBlank()
    return parameters.takeIf { hasCode != hasError }
}

private fun randomOAuthValue(): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

private fun oauthForm(parameters: Map<String, String>): String =
    parameters.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, UTF_8)}=${URLEncoder.encode(it.value, UTF_8)}"
    }
