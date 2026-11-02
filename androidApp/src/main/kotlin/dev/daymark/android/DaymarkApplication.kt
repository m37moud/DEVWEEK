package dev.daymark.android

import android.app.Application

class DaymarkApplication : Application() {
    val graph by lazy { AndroidAppGraph(this) }
    internal var locationPermissions: AndroidLocationPermissions? = null
    internal lateinit var googleAuth: AndroidGoogleAuth
    override fun onCreate() {
        super.onCreate()
        googleAuth = AndroidGoogleAuth(this)
        graph.requestRecovery()
    }
}
