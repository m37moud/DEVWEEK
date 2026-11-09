package dev.daymark.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import dev.daymark.logging.AppLogger
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidPlaceGeocoder(context: Context) : PlaceGeocoder {
    private val app = context.applicationContext

    override suspend fun address(request: PlaceAddressRequest): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(app, Locale.forLanguageTag(request.language.tag))
        return if (Build.VERSION.SDK_INT >= 33) asyncAddress(geocoder, request)
        else legacyAddress(geocoder, request)
    }

    @androidx.annotation.RequiresApi(33)
    private suspend fun asyncAddress(geocoder: Geocoder, request: PlaceAddressRequest): String? {
        val reply = CompletableDeferred<String?>()
        geocoder.getFromLocation(request.latitude, request.longitude, 1, listener(reply))
        return try {
            reply.await()
        } finally {
            reply.cancel()
        }
    }

    @androidx.annotation.RequiresApi(33)
    private fun listener(reply: CompletableDeferred<String?>) = object : Geocoder.GeocodeListener {
        override fun onGeocode(addresses: MutableList<Address>) {
            reply.complete(addresses.firstOrNull()?.getAddressLine(0))
        }

        override fun onError(errorMessage: String?) {
            AppLogger.i("PlaceAddress", "Native address provider unavailable")
            reply.complete(null)
        }
    }

    private suspend fun legacyAddress(geocoder: Geocoder, request: PlaceAddressRequest): String? =
        suspendCancellableCoroutine { continuation ->
            val worker = Thread {
                continuation.resume(blockingAddress(geocoder, request))
            }
            worker.isDaemon = true
            continuation.invokeOnCancellation { worker.interrupt() }
            worker.start()
        }

    @Suppress("DEPRECATION")
    private fun blockingAddress(geocoder: Geocoder, request: PlaceAddressRequest): String? = try {
        geocoder.getFromLocation(request.latitude, request.longitude, 1)
            ?.firstOrNull()?.getAddressLine(0)
    } catch (failure: IOException) {
        AppLogger.e("PlaceAddress", "Native address lookup failed", failure)
        null
    }
}
