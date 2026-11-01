package dev.daymark.widgets

/** Native atomic snapshot storage; publication failure never rolls back attendance. */
interface WidgetSink {
    fun publish(json: String): Boolean
    fun clear(): Boolean
}
