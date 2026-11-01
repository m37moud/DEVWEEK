package dev.daymark.app

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Native taps carry no historical date and never persist attendance. The foreground host consumes one current check intent. */
object QuickCheckIntents {
    private val pending = Channel<Unit>(Channel.CONFLATED)
    val events = pending.receiveAsFlow()
    fun request() { pending.trySend(Unit) }
}
