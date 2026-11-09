package dev.daymark.repository

import dev.daymark.location.PlaceAddressRequest

fun interface PlaceAddressDataSource {
    /** Returns a cached or newly saved address, or null on lookup/storage failure or an obsolete session. */
    suspend fun resolve(request: PlaceAddressRequest): String?
}
