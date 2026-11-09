package dev.daymark.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AccessResult {
    class Granted(val token: String) : AccessResult {
        override fun toString(): String = "Granted(token=<redacted>)"
    }

    data object NeedsInteraction : AccessResult
    data object Cancelled : AccessResult
    class Failed(val errorKey: String) : AccessResult {
        override fun toString(): String = "Failed(authorization)"
    }
}

class GoogleAccess(private val native: NativeGoogleAuth) {
    private val requests = Mutex()
    private val generation = MutableStateFlow(0L)

    suspend fun accessToken(interactive: Boolean): AccessResult = requests.withLock {
        val requestedGeneration = generation.value
        val reply = CompletableDeferred<NativeAuthReply>()
        try {
            native.authorize(interactive) { reply.complete(it) }
            val result = reply.await()
            if (generation.value != requestedGeneration) return@withLock AccessResult.NeedsInteraction
            when (result.status) {
                NativeAuthStatus.GRANTED -> result.accessToken?.takeIf { it.isNotBlank() }
                    ?.let { AccessResult.Granted(it) }
                    ?: AccessResult.Failed("authorization_failed")

                NativeAuthStatus.NEEDS_INTERACTION -> AccessResult.NeedsInteraction
                NativeAuthStatus.CANCELLED -> AccessResult.Cancelled
                NativeAuthStatus.FAILED -> AccessResult.Failed(
                    if (result.errorKey == "authorization_configuration") "authorization_configuration"
                    else "authorization_failed"
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AccessResult.Failed("authorization_failed")
        } finally {
            reply.cancel()
        }
    }

    suspend fun forget() {
        generation.update { it + 1 }
        val completed = CompletableDeferred<Boolean>()
        try {
            native.forget { completed.complete(it) }
            check(completed.await()) { "native_session_not_cleared" }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw IllegalStateException("native_session_not_cleared")
        } finally {
            completed.cancel()
        }
    }
}
