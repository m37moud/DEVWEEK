package dev.daymark.android

import android.app.Application

class DaymarkApplication : Application() {
    internal var locationPermissions: AndroidLocationPermissions? = null
}
