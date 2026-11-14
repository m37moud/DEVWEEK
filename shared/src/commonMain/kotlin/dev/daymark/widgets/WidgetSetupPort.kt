package dev.daymark.widgets

/** OS configuration status, independent of attendance and pin-request acceptance. */
enum class WidgetSetupStatus { CHECKING, NOT_ADDED, ADDED, UNKNOWN, UNSUPPORTED }

interface WidgetSetupPort {
    val canRequestPin: Boolean
    fun query(completion: (WidgetSetupStatus) -> Unit)
    fun requestPin(onConfirmed: () -> Unit): Boolean
    fun close() {}
}

internal expect fun createWidgetSetupPort(): WidgetSetupPort

object UnsupportedWidgetSetupPort : WidgetSetupPort {
    override val canRequestPin = false
    override fun query(completion: (WidgetSetupStatus) -> Unit) =
        completion(WidgetSetupStatus.UNSUPPORTED)

    override fun requestPin(onConfirmed: () -> Unit) = false
}
