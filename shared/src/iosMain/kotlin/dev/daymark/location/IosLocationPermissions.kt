@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.daymark.location

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreLocation.*
import platform.Foundation.NSURL
import platform.UIKit.*
import platform.darwin.NSObject

internal interface IosPermissionActions {
    fun current(): LocationCapabilities
    fun requestWhenInUse(complete: () -> Unit): () -> Unit
    fun openAppSettings()
}

/** Construct on the host's main queue; native UI requests are also dispatched there. */
class IosLocationPermissions internal constructor(private val actions: IosPermissionActions) : LocationPermissionPort {
    constructor() : this(CoreAuthorizationActions())
    private val mutex = Mutex()
    override fun current(): LocationCapabilities = actions.current()
    override suspend fun requestForeground(): LocationCapabilities = mutex.withLock {
        val before = current()
        if (before.foregroundGranted) return@withLock before
        val reply = CompletableDeferred<Unit>()
        val detach = actions.requestWhenInUse { reply.complete(Unit) }
        try { reply.await(); current() } finally { reply.cancel(); detach() }
    }
    override suspend fun requestBackground(): LocationCapabilities = current()
    override fun openSystemSettings() { actions.openAppSettings() }
}

private class CoreAuthorizationActions : IosPermissionActions {
    private val delegate = CoreAuthorizationDelegate()
    override fun current(): LocationCapabilities = delegate.current()
    override fun requestWhenInUse(complete: () -> Unit): () -> Unit = delegate.requestWhenInUse(complete)
    override fun openAppSettings() { delegate.openAppSettings() }
}

private class CoreAuthorizationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private var pending: (() -> Unit)? = null
    init { manager.delegate = this }
    fun current(): LocationCapabilities = iosLocationCapabilities(manager)

    fun requestWhenInUse(complete: () -> Unit): () -> Unit {
        val lifetime = Job()
        val once = { if (lifetime.complete()) complete() }
        lifetime.invokeOnCompletion { onIosLocationMain { if (pending === once) pending = null } }
        onIosLocationMain {
            if (lifetime.isActive) {
                if (manager.authorizationStatus == kCLAuthorizationStatusNotDetermined) {
                    pending = once
                    manager.requestWhenInUseAuthorization()
                } else once() // Denied/restricted states do not promise another delegate callback.
            }
        }
        return { lifetime.cancel() }
    }
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (manager.authorizationStatus != kCLAuthorizationStatusNotDetermined) pending?.invoke()
    }
    fun openAppSettings() {
        onIosLocationMain {
            val url = NSURL(string = UIApplicationOpenSettingsURLString)
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }
}
