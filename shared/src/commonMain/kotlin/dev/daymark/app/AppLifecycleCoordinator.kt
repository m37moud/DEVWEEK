package dev.daymark.app

import dev.daymark.models.reporting.SyncPassResult
import dev.daymark.models.reporting.SyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Owns foreground work only; an Android network worker has an independent lifecycle. */
class AppLifecycleCoordinator(
    private val scope: CoroutineScope,
    private val runSync: suspend (SyncTrigger) -> SyncPassResult,
    private val nextDelay: suspend () -> Long? = { null },
    private val enqueueNetwork: () -> Unit = {},
    private val observeConnectivity: ((() -> Unit) -> (() -> Unit)) = { {} }
) {
    private val events = Channel<SyncTrigger>(Channel.CONFLATED)
    var isActive = false
        private set
    private var started = false
    private var foreground: Job? = null
    private var stopConnectivity: (() -> Unit)? = null
    fun onActive(value: Boolean) {
        if (isActive == value) return
        isActive = value
        if (!value) {
            stopConnectivity?.invoke(); stopConnectivity = null
            foreground?.cancel(); foreground = null
            while (events.tryReceive().isSuccess) { /* Discard old foreground triggers. */
            }
            return
        }
        val initial = if (started) SyncTrigger.RESUME else SyncTrigger.LAUNCH
        started = true
        foreground = scope.launch {
            var trigger = initial
            while (isActive) {
                val result = runSync(trigger)
                val wait = if (result == SyncPassResult.Pending) nextDelay() else null
                trigger =
                    if (wait == null) events.receive() else withTimeoutOrNull(wait.coerceAtLeast(1)) { events.receive() }
                        ?: SyncTrigger.RETRY
            }
        }
        stopConnectivity = observeConnectivity { events.trySend(SyncTrigger.CONNECTIVITY) }
    }

    fun onLocalChange() {
        enqueueNetwork(); if (isActive) events.trySend(SyncTrigger.LOCAL_CHANGE)
    }

    fun retryReport() {
        enqueueNetwork(); if (isActive) events.trySend(SyncTrigger.USER_RETRY)
    }

    fun onReconnect() {
        enqueueNetwork(); if (isActive) events.trySend(SyncTrigger.RECONNECT)
    }

    suspend fun stop() {
        val running = foreground; onActive(false); running?.join()
    }
}
