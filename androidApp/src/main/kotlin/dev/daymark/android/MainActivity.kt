package dev.daymark.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        (application as DaymarkApplication).graph.requestRecovery()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DaymarkApplication
        val permissions = app.locationPermissions?.also { it.attach(this) }
            ?: AndroidLocationPermissions(this).also { app.locationPermissions = it }
        val googleAuth = app.googleAuth.also { it.attach(this) }
        setContent { DaymarkHost(permissions, googleAuth) }
    }
}
