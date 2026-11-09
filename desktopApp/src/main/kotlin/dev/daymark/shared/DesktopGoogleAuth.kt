package dev.daymark.shared

import dev.daymark.auth.GOOGLE_REPORT_SCOPES
import dev.daymark.auth.NativeAuthReply
import dev.daymark.auth.NativeAuthStatus
import dev.daymark.auth.NativeGoogleAuth
import dev.daymark.logging.AppLogger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class DesktopGoogleAuth(
    private val client: () -> DesktopOAuthClient? = { loadDesktopOAuthClient() },
    private val store: DesktopCredentialStore = desktopCredentialStore(),
    private val flow: DesktopOAuthFlow = DesktopOAuthFlow()
) : NativeGoogleAuth {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicLong()
    private val sessions = Mutex()

    @Volatile
    private var pending: Job? = null

    override fun authorize(interactive: Boolean, completion: (NativeAuthReply) -> Unit) {
        val requestedGeneration = generation.get()
        pending = scope.launch {
            try {
                val reply = authorizationReply(interactive, requestedGeneration)
                completion(
                    if (generation.get() == requestedGeneration) reply
                    else NativeAuthReply(NativeAuthStatus.NEEDS_INTERACTION, null, null)
                )
            } catch (cancelled: CancellationException) {
                completion(NativeAuthReply(NativeAuthStatus.NEEDS_INTERACTION, null, null))
                throw cancelled
            }
        }
    }

    private suspend fun authorizationReply(
        interactive: Boolean,
        requestedGeneration: Long
    ): NativeAuthReply = try {
        authorize(interactive, requestedGeneration)
    } catch (_: TimeoutCancellationException) {
        AppLogger.i("DesktopGoogleAuth", "Authorization timed out")
        NativeAuthReply(NativeAuthStatus.CANCELLED, null, null)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        AppLogger.e("DesktopGoogleAuth", "Authorization failed", failure)
        NativeAuthReply(NativeAuthStatus.FAILED, null, "authorization_failed")
    }

    private suspend fun authorize(
        interactive: Boolean,
        requestedGeneration: Long
    ): NativeAuthReply {
        val configuration = client() ?: return NativeAuthReply(
            NativeAuthStatus.FAILED, null, "authorization_configuration"
        )
        val refresh = if (interactive) null else savedRefreshToken(configuration)
        if (!interactive && refresh == null) return NativeAuthReply(
            NativeAuthStatus.NEEDS_INTERACTION,
            null,
            null
        )
        AppLogger.d("DesktopGoogleAuth", "Starting authorization")
        val tokens = if (interactive) flow.authorize(configuration) else flow.refresh(
            configuration,
            refresh!!
        )
        if (tokens == null) return NativeAuthReply(NativeAuthStatus.CANCELLED, null, null)
        val accessToken = validatedAccessToken(tokens)
        val nextRefresh = tokens["refresh_token"]?.jsonPrimitive?.content ?: refresh
        check(!nextRefresh.isNullOrBlank()) { "session_unavailable" }
        if (!saveSession(configuration, nextRefresh, requestedGeneration))
            return NativeAuthReply(NativeAuthStatus.NEEDS_INTERACTION, null, null)
        AppLogger.i("DesktopGoogleAuth", "Authorization completed")
        return NativeAuthReply(NativeAuthStatus.GRANTED, accessToken, null)
    }

    private suspend fun savedRefreshToken(configuration: DesktopOAuthClient): String? {
        val saved =
            sessions.withLock { store.read() }?.let { Json.parseToJsonElement(it).jsonObject }
        return saved?.takeIf { it["client_id"]?.jsonPrimitive?.content == configuration.id }
            ?.get("refresh_token")?.jsonPrimitive?.content
    }

    private fun validatedAccessToken(tokens: JsonObject): String {
        val accessToken = tokens["access_token"]?.jsonPrimitive?.content
        check(
            !accessToken.isNullOrBlank() && tokens["token_type"]?.jsonPrimitive?.content.equals(
                "Bearer",
                true
            )
        )
        tokens["scope"]?.jsonPrimitive?.content?.let {
            check(it.split(' ').containsAll(GOOGLE_REPORT_SCOPES))
        }
        return accessToken
    }

    private suspend fun saveSession(
        configuration: DesktopOAuthClient,
        refreshToken: String,
        requestedGeneration: Long
    ): Boolean =
        sessions.withLock {
            if (generation.get() != requestedGeneration) return@withLock false
            store.write(buildJsonObject {
                put("client_id", configuration.id)
                put("refresh_token", refreshToken)
            }.toString())
            true
        }

    override fun forget(completion: (Boolean) -> Unit) {
        generation.incrementAndGet()
        pending?.cancel()
        scope.launch {
            try {
                sessions.withLock { store.clear() }
                AppLogger.i("DesktopGoogleAuth", "Local authorization cleared")
                completion(true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.e("DesktopGoogleAuth", "Session removal failed", failure)
                completion(false)
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}
