package dev.daymark.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocationCapabilityPolicyTest {
    @Test
    fun approximate_access_is_not_enough_for_presence_verification() {
        assertEquals(
            LocationFailure.PERMISSION_DENIED,
            locationGateFailure(LocationCapabilities(true, true, false, true))
        )
    }

    @Test
    fun disabled_services_are_reported_without_requesting_location() {
        assertEquals(
            LocationFailure.SERVICES_DISABLED,
            locationGateFailure(LocationCapabilities(true, true, true, false))
        )
    }

    @Test
    fun foreground_checks_do_not_require_background_access() {
        assertNull(locationGateFailure(LocationCapabilities(true, false, true, true)))
    }
}
