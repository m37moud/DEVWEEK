@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package dev.daymark.app

import androidx.compose.material3.Text
import androidx.compose.ui.window.ComposeUIViewController
import dev.daymark.location.IosLocationGateway
import dev.daymark.location.IosLocationPermissions
import dev.daymark.presentation.component.LocationProbeContent
import platform.UIKit.UIViewController

fun NativeProbeViewController(): UIViewController {
    if (!Platform.isDebugBinary) return ComposeUIViewController { Text("Native location probes require a Kotlin debug build") }
    val gateway = IosLocationGateway()
    val permissions = IosLocationPermissions()
    return ComposeUIViewController { LocationProbeContent(gateway, permissions) }
}
