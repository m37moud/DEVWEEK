package dev.daymark.ui

import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.presentation.component.previewCoordinate
import dev.daymark.presentation.component.registeredPlaceZoom
import dev.daymark.settings.AppLanguage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegisteredPlacePreviewTest {
    private val place = RegisteredPlace(0.0, 179.999, 100, 1)

    @Test
    fun larger_radius_zooms_out_and_larger_viewport_zooms_in() {
        val initial = registeredPlaceZoom(place, 240f)
        assertTrue(
            abs(
                initial - registeredPlaceZoom(
                    place.copy(radiusMetres = 200),
                    240f
                ) - 1
            ) < 0.00001
        )
        assertTrue(abs(registeredPlaceZoom(place, 480f) - initial - 1) < 0.00001)
    }

    @Test
    fun latitude_changes_scale_without_moving_the_registered_position() {
        val equator = registeredPlaceZoom(place, 240f)
        val north = registeredPlaceZoom(place.copy(latitude = 60.0), 240f)
        val south = registeredPlaceZoom(place.copy(latitude = -60.0), 240f)
        assertTrue(abs(equator - north - 1) < 0.00001)
        assertEquals(north, south)
        assertEquals(equator, registeredPlaceZoom(place.copy(longitude = -179.999), 240f))
    }

    @Test
    fun coordinates_have_fixed_precision_localized_digits_and_no_negative_zero() {
        assertEquals("-12.34568", previewCoordinate(-12.345678, AppLanguage.EN))
        assertEquals("٠٫٠٠٠٠٠", previewCoordinate(-0.000001, AppLanguage.AR))
        assertEquals("١٨٠٫٠٠٠٠٠", previewCoordinate(180.0, AppLanguage.AR))
    }
}
