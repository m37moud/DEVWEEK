package dev.daymark.data.local.address

import androidx.sqlite.SQLiteException
import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.dao.PlaceAddressDao
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.location.PlaceAddressRequest
import dev.daymark.location.PlaceGeocoder
import dev.daymark.logging.AppLogger
import dev.daymark.repository.PlaceAddressDataSource
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

class RoomPlaceAddressDataSource(
    private val profileDao: ProfileDao,
    private val placeAddressDao: PlaceAddressDao, private val transactions: DatabaseTransactions,
    private val geocoder: PlaceGeocoder
) : PlaceAddressDataSource {

    override suspend fun resolve(request: PlaceAddressRequest): String? = try {
        resolveForSession(request)
    } catch (failure: SQLiteException) {
        AppLogger.e("PlaceAddress", "Address cache unavailable", failure)
        null
    }

    private suspend fun resolveForSession(request: PlaceAddressRequest): String? {
        val owner = transactions.withWriteTransaction<String?> { localOwner() } ?: return null
        val cached =
            placeAddressDao.placeAddress(request.latitude, request.longitude, request.language.tag)
        if (cached != null) {
            AppLogger.d("PlaceAddress", "Using cached address")
            return cached.address.takeIf { localOwner() == owner }
        }
        AppLogger.d("PlaceAddress", "Starting native address lookup")
        val address = withTimeoutOrNull(10.seconds) { geocoder.address(request) }
            ?.trim()?.takeIf { it.isNotEmpty() }
        if (address == null) {
            AppLogger.i("PlaceAddress", "Address lookup unavailable")
            return null
        }
        return saveForSession(request, address, owner)
    }

    private suspend fun saveForSession(
        request: PlaceAddressRequest,
        address: String,
        owner: String
    ): String? = transactions.withWriteTransaction {
        currentCoroutineContext().ensureActive()
        // A deleted installation or replaced setup session must not accept an old native callback.
        if (localOwner() != owner) return@withWriteTransaction null
        placeAddressDao.putPlaceAddress(
            PlaceAddressEntity(request.latitude, request.longitude, request.language.tag, address)
        )
        address
    }.also { saved ->
        if (saved != null) AppLogger.i("PlaceAddress", "Address cached")
    }

    private suspend fun localOwner(): String? =
        profileDao.tracker()?.id ?: profileDao.setting("setup-session")?.value
}
