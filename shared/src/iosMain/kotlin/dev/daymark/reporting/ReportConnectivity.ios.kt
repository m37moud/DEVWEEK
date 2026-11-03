package dev.daymark.reporting

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
actual fun observeReportConnectivity(onAvailable: () -> Unit): () -> Unit {
    val monitor = nw_path_monitor_create() ?: return {}
    nw_path_monitor_set_update_handler(monitor) { path ->
        if (path != null && nw_path_get_status(path) == nw_path_status_satisfied) onAvailable()
    }
    nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
    nw_path_monitor_start(monitor)
    return { nw_path_monitor_cancel(monitor) }
}
