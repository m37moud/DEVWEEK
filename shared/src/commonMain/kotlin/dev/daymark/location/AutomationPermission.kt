package dev.daymark.location

internal val LocationCapabilities.canRunAutomaticCheck: Boolean
    get() = foregroundGranted && preciseGranted && backgroundGranted && servicesEnabled

internal suspend fun LocationPermissionPort.requestAutomation(): LocationCapabilities {
    var capability = current()
    if (!capability.foregroundGranted || !capability.preciseGranted) {
        capability = requestForeground()
    }
    if (capability.foregroundGranted && capability.preciseGranted && !capability.backgroundGranted) {
        capability = requestBackground()
    }
    return capability
}
