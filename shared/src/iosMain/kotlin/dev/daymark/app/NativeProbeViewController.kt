@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package dev.daymark.app

import androidx.compose.ui.window.ComposeUIViewController
import dev.daymark.location.IosLocationGateway
import dev.daymark.location.IosLocationPermissions
import dev.daymark.ui.LocationProbeContent
import kotlin.native.Platform
import platform.UIKit.UIViewController

fun NativeProbeViewController(): UIViewController {
    if (!Platform.isDebugBinary) return MainViewController()
    val gateway = IosLocationGateway()
    val permissions = IosLocationPermissions()
    return ComposeUIViewController { LocationProbeContent(gateway, permissions) }
}
