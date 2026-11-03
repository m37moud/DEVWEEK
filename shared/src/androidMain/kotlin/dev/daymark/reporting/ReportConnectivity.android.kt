package dev.daymark.reporting

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

private var connectivityContext: Context? = null
fun initializeReportConnectivity(context: Context) {
    connectivityContext = context.applicationContext
}

actual fun observeReportConnectivity(onAvailable: () -> Unit): () -> Unit {
    val manager =
        connectivityContext?.getSystemService(ConnectivityManager::class.java) ?: return {}
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onAvailable()
        }
    }
    return try {
        manager.registerDefaultNetworkCallback(callback)
        val stop: () -> Unit = { runCatching { manager.unregisterNetworkCallback(callback) }; Unit }
        stop
    } catch (_: SecurityException) {
        {}
    }
}
