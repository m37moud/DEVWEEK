package dev.daymark.location

import dev.daymark.settings.AppLanguage

data class PlaceAddressRequest(
    val latitude: Double,
    val longitude: Double,
    val language: AppLanguage
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }

    override fun toString() = "PlaceAddressRequest(coordinates=<redacted>, language=$language)"
}

fun interface PlaceGeocoder {
    /** Returns null when the native provider is unavailable or has no matching address. */
    suspend fun address(request: PlaceAddressRequest): String?
}
