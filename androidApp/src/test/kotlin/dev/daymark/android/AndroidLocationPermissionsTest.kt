package dev.daymark.android

import dev.daymark.location.LocationCapabilities
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.junit.Test

private class PermissionActions(override val sdkInt: Int) : AndroidPermissionActions {
    var caps = LocationCapabilities(false, false, false, true)
    var foregroundRequests = 0
    var backgroundRequests = 0
    var settingsRequests = 0
    var cancellations = 0
    var closes = 0
    var callback: (() -> Unit)? = null
    override fun current() = caps
    private fun pending(complete: () -> Unit): () -> Unit {
        callback = complete
        return { cancellations++ }
    }
    override fun requestForeground(complete: () -> Unit): () -> Unit { foregroundRequests++; return pending(complete) }
    override fun requestBackground(complete: () -> Unit): () -> Unit { backgroundRequests++; return pending(complete) }
    override fun openBackgroundSettings(complete: () -> Unit): () -> Unit { settingsRequests++; return pending(complete) }
    override fun openAppSettings() { settingsRequests++ }
    override fun close() { closes++; callback?.invoke(); callback = null }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidLocationPermissionsTest {
    @Test fun foreground_result_uses_the_current_native_permission_snapshot() = runTest {
        val native = PermissionActions(35)
        val port = AndroidLocationPermissions(native)
        val request = async { port.requestForeground() }
        runCurrent()
        assertFalse(request.isCompleted)
        native.caps = LocationCapabilities(true, false, true, true)
        requireNotNull(native.callback).invoke()
        assertTrue(request.await().preciseGranted)
        port.requestForeground()
        assertEquals(1, native.foregroundRequests)
    }
    @Test fun android_eleven_background_access_opens_settings_separately() = runTest {
        val native = PermissionActions(30)
        native.caps = LocationCapabilities(true, false, true, true)
        val request = async { AndroidLocationPermissions(native).requestBackground() }
        runCurrent()
        assertEquals(0, native.backgroundRequests)
        assertEquals(1, native.settingsRequests)
        native.caps = native.caps.copy(backgroundGranted = true)
        requireNotNull(native.callback).invoke()
        assertTrue(request.await().backgroundGranted)
    }
    @Test fun android_ten_uses_the_separate_background_permission_request() = runTest {
        val native = PermissionActions(29)
        native.caps = LocationCapabilities(true, false, true, true)
        val request = async { AndroidLocationPermissions(native).requestBackground() }
        runCurrent()
        assertEquals(1, native.backgroundRequests)
        assertEquals(0, native.settingsRequests)
        requireNotNull(native.callback).invoke()
        assertFalse(request.await().backgroundGranted)
    }
    @Test fun background_access_never_bypasses_missing_foreground_access() = runTest {
        val native = PermissionActions(35)
        assertFalse(AndroidLocationPermissions(native).requestBackground().backgroundGranted)
        assertEquals(0, native.backgroundRequests + native.settingsRequests + native.foregroundRequests)
    }
    @Test fun caller_cancellation_detaches_a_late_permission_callback() = runTest {
        val native = PermissionActions(35)
        val request = async { AndroidLocationPermissions(native).requestForeground() }
        runCurrent()
        request.cancel()
        runCurrent()
        assertEquals(1, native.cancellations)
        requireNotNull(native.callback).invoke()
        assertTrue(request.isCancelled)
    }
    @Test fun replacing_the_activity_backend_releases_the_old_request() = runTest {
        val previous = PermissionActions(35)
        val port = AndroidLocationPermissions(previous)
        val pending = async { port.requestForeground() }
        runCurrent()
        val next = PermissionActions(35)
        next.caps = LocationCapabilities(true, false, true, true)
        port.bindActions(next)
        assertTrue(pending.await().preciseGranted)
        assertEquals(1, previous.closes)
        port.requestForeground()
        assertEquals(0, next.foregroundRequests)
    }

}
