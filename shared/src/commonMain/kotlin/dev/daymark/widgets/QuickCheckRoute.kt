package dev.daymark.widgets

import kotlin.uuid.Uuid

data class QuickCheckRoute(val schemaVersion: Int = 1,val trackerId: String) {
    init { require(schemaVersion == 1); Uuid.parse(trackerId) }
}
fun encodeQuickCheckRoute(route: QuickCheckRoute): String = "daymark://quick-check?v=1&trackerId=${Uuid.parse(route.trackerId)}"
private val routeShape = Regex("^daymark://quick-check\\?v=1&trackerId=([a-fA-F0-9-]{36})$")
fun parseQuickCheckRoute(raw: String): QuickCheckRoute? {
    if (raw.length > 128) return null
    val id = routeShape.matchEntire(raw)?.groupValues?.get(1) ?: return null
    return runCatching { QuickCheckRoute(trackerId = Uuid.parse(id).toString()) }.getOrNull()
}
