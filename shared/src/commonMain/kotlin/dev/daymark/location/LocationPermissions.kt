package dev.daymark.location

interface LocationPermissionPort {
    fun current(): LocationCapabilities
    suspend fun requestForeground(): LocationCapabilities
    suspend fun requestBackground(): LocationCapabilities
    fun openSystemSettings()
}

internal fun locationGateFailure(capabilities: LocationCapabilities): LocationFailure? = when {
    !capabilities.foregroundGranted || !capabilities.preciseGranted -> LocationFailure.PERMISSION_DENIED
    !capabilities.servicesEnabled -> LocationFailure.SERVICES_DISABLED
    else -> null
}
