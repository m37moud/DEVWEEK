@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.daymark.location

import dev.daymark.logging.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import platform.Foundation.NSLocale

// shortcut: CLGeocoder preserves iOS 16 support; migrate to MapKit when the floor reaches iOS 26.
@Suppress("DEPRECATION")
class IosPlaceGeocoder : PlaceGeocoder {
    override suspend fun address(request: PlaceAddressRequest): String? =
        withContext(Dispatchers.Main.immediate) {
            val geocoder = CLGeocoder()
            val reply = CompletableDeferred<String?>()
            geocoder.lookup(request, reply)
            try {
                reply.await()
            } finally {
                reply.cancel()
                geocoder.cancelGeocode()
            }
        }

    private fun CLGeocoder.lookup(request: PlaceAddressRequest, reply: CompletableDeferred<String?>) {
        reverseGeocodeLocation(
            CLLocation(request.latitude, request.longitude),
            preferredLocale = NSLocale(request.language.tag)
        ) { placemarks, error ->
            if (error != null) AppLogger.i("PlaceAddress", "Native address provider unavailable")
            val placemark = if (error == null) placemarks?.firstOrNull() as? CLPlacemark else null
            reply.complete(placemark?.descriptionText())
        }
    }
}

private fun CLPlacemark.descriptionText(): String? =
    listOfNotNull(name, thoroughfare, subLocality, locality, administrativeArea, country)
        .map(String::trim).filter(String::isNotEmpty).distinct()
        .joinToString(", ").takeIf(String::isNotEmpty)
