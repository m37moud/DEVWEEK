package dev.daymark.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.androidLocationCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface AndroidPermissionActions {
    val sdkInt: Int
    fun current(): LocationCapabilities
    fun requestForeground(complete: () -> Unit): () -> Unit
    fun requestBackground(complete: () -> Unit): () -> Unit
    fun openBackgroundSettings(complete: () -> Unit): () -> Unit
    fun openAppSettings()
    fun close()
}

/** Keep this port across activity recreation; attach the current activity before STARTED. */
class AndroidLocationPermissions private constructor(
    initial: AndroidPermissionActions,
    private val fallbackSnapshot: () -> LocationCapabilities,
) : LocationPermissionPort {
    internal constructor(actions: AndroidPermissionActions) : this(actions, actions::current)
    constructor(activity: ComponentActivity) : this(ActivityPermissionActions(activity), snapshot(activity.applicationContext)) {
        observe(activity, requireNotNull(actions))
    }
    @Volatile private var actions: AndroidPermissionActions? = initial
    private val mutex = Mutex()
    override fun current(): LocationCapabilities = actions?.current() ?: fallbackSnapshot()

    fun attach(activity: ComponentActivity) {
        val next = ActivityPermissionActions(activity)
        bindActions(next)
        observe(activity, next)
    }
    internal fun bindActions(next: AndroidPermissionActions) {
        val previous = actions
        actions = next
        previous?.close()
    }
    private fun observe(activity: ComponentActivity, bound: AndroidPermissionActions) {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (actions === bound) actions = null
                bound.close()
            }
        })
    }
    override suspend fun requestForeground(): LocationCapabilities = mutex.withLock {
        val before = current()
        if (before.foregroundGranted && before.preciseGranted) return@withLock before
        val bound = actions ?: return@withLock before
        awaitPermission(bound::requestForeground)
        current()
    }
    override suspend fun requestBackground(): LocationCapabilities = mutex.withLock {
        val before = current()
        val bound = actions ?: return@withLock before
        if (!before.foregroundGranted || !before.preciseGranted || before.backgroundGranted || bound.sdkInt < 29)
            return@withLock before
        if (bound.sdkInt == 29) awaitPermission(bound::requestBackground)
        else awaitPermission(bound::openBackgroundSettings)
        current()
    }
    override fun openSystemSettings() { actions?.openAppSettings() }

    private suspend fun awaitPermission(register: (() -> Unit) -> (() -> Unit)) {
        val reply = CompletableDeferred<Unit>()
        val detach = register { reply.complete(Unit) }
        try { reply.await() } finally { reply.cancel(); detach() }
    }
    private companion object {
        fun snapshot(context: Context): () -> LocationCapabilities = { androidLocationCapabilities(context) }
    }
}

private class PermissionSlot {
    var completion: (() -> Unit)? = null
    fun finish() { val next = completion; completion = null; next?.invoke() }
}

private class ActivityPermissionActions(private val activity: ComponentActivity) : AndroidPermissionActions {
    private val context = activity.applicationContext
    private val foreground = PermissionSlot()
    private val background = PermissionSlot()
    private val settings = PermissionSlot()
    override val sdkInt: Int get() = Build.VERSION.SDK_INT
    private val foregroundLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { foreground.finish() }
    private val backgroundLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { background.finish() }
    private val settingsLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { settings.finish() }
    override fun current(): LocationCapabilities = androidLocationCapabilities(context)
    override fun requestForeground(complete: () -> Unit): () -> Unit =
        launch(foreground, complete) {
            foregroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    override fun requestBackground(complete: () -> Unit): () -> Unit =
        launch(background, complete) { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    override fun openBackgroundSettings(complete: () -> Unit): () -> Unit =
        launch(settings, complete) { settingsLauncher.launch(appSettingsIntent()) }
    override fun openAppSettings() { activity.runOnUiThread { activity.startActivity(appSettingsIntent()) } }
    private fun appSettingsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null))

    private fun launch(slot: PermissionSlot, complete: () -> Unit, action: () -> Unit): () -> Unit {
        val lifetime = Job()
        val once = { if (lifetime.complete()) complete() }
        lifetime.invokeOnCompletion {
            activity.runOnUiThread { if (slot.completion === once) slot.completion = null }
        }
        activity.runOnUiThread {
            if (lifetime.isActive) { slot.completion = once; action() }
        }
        return { lifetime.cancel() }
    }
    override fun close() { foreground.finish(); background.finish(); settings.finish() }
}
