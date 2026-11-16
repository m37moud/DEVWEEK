package dev.daymark.app

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Opens current local state without requesting GPS. */
object TodayIntents {
    private val pending = Channel<Unit>(Channel.CONFLATED)
    val events = pending.receiveAsFlow()
    fun request() {
        pending.trySend(Unit)
    }
}
