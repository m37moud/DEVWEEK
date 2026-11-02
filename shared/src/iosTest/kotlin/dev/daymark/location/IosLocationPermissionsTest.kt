package dev.daymark.location

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

private class AuthorizationActions : IosPermissionActions {
    var caps = LocationCapabilities(false, false, false, true)
    var requests = 0
    var cancellations = 0
    var callback: (() -> Unit)? = null
    override fun current() = caps
    override fun requestWhenInUse(complete: () -> Unit): () -> Unit {
        requests++
        callback = complete
        return { cancellations++ }
    }
    override fun openAppSettings() = Unit
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IosLocationPermissionsTest {
    @Test fun background_access_never_requests_always_or_when_in_use_permission() = runTest {
        val native = AuthorizationActions()
        assertFalse(IosLocationPermissions(native).requestBackground().backgroundGranted)
        assertEquals(0, native.requests)
    }
    @Test fun foreground_request_returns_the_observed_grant() = runTest {
        val native = AuthorizationActions()
        val request = async { IosLocationPermissions(native).requestForeground() }
        runCurrent()
        assertFalse(request.isCompleted)
        native.caps = LocationCapabilities(true, false, true, true)
        requireNotNull(native.callback).invoke()
        assertTrue(request.await().preciseGranted)
        assertEquals(1, native.requests)
        assertEquals(1, native.cancellations)
    }
    @Test fun existing_approximate_access_requires_settings_not_a_repeated_native_prompt() = runTest {
        val native = AuthorizationActions()
        native.caps = LocationCapabilities(true, false, false, true)
        val result = IosLocationPermissions(native).requestForeground()
        assertFalse(result.preciseGranted)
        assertEquals(0, native.requests)
    }
}
