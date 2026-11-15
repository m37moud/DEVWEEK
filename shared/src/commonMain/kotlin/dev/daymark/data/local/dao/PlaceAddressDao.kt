package dev.daymark.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.daymark.data.local.address.PlaceAddressEntity

@Dao
interface PlaceAddressDao {
    @Query("SELECT * FROM place_addresses WHERE latitude = :latitude AND longitude = :longitude AND language = :language")
    suspend fun placeAddress(
        latitude: Double,
        longitude: Double,
        language: String
    ): PlaceAddressEntity?

    @Upsert
    suspend fun putPlaceAddress(address: PlaceAddressEntity)
}
