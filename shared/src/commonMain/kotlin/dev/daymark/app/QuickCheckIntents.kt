package dev.daymark.app

import dev.daymark.widgets.QuickCheckRoute
import dev.daymark.widgets.parseQuickCheckRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.uuid.Uuid

/** Navigation intent only. No historical date, fix or attendance evidence is accepted. */
data class QuickCheckRequest(
    val token: String,
    val route: QuickCheckRoute?,
    val allowCurrentTracker: Boolean
)

object QuickCheckIntents {
    private val pending = Channel<QuickCheckRequest>(Channel.CONFLATED)
    val events = pending.receiveAsFlow()
    fun request() {
        pending.trySend(QuickCheckRequest(Uuid.random().toString(), null, true))
    }

    fun requestRoute(raw: String) {
        pending.trySend(
            QuickCheckRequest(
                Uuid.random().toString(),
                parseQuickCheckRoute(raw),
                false
            )
        )
    }
}
