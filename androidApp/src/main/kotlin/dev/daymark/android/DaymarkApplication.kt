package dev.daymark.android

import android.app.Application

class DaymarkApplication : Application() {
    val graph by lazy { AndroidAppGraph(this) }
    internal var locationPermissions: AndroidLocationPermissions? = null
    internal val googleAuth get() = graph.googleAuth
    override fun onCreate() {
        super.onCreate()
        graph.requestRecovery()
    }
}
