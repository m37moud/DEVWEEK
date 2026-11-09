package dev.daymark.data.local.address

import androidx.room3.Entity

@Entity(tableName = "place_addresses", primaryKeys = ["latitude", "longitude", "language"])
data class PlaceAddressEntity(
    val latitude: Double,
    val longitude: Double,
    val language: String,
    val address: String
) {
    override fun toString() = "PlaceAddressEntity(metadata=<redacted>)"
}
