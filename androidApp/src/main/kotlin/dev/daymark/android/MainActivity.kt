package dev.daymark.android

import android.os.Bundle
import android.content.Intent
import dev.daymark.app.QuickCheckIntents
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private var detachResultNotifications: (() -> Unit)? = null

    override fun onDestroy() {
        detachResultNotifications?.invoke()
        detachResultNotifications = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        WidgetCheckState.clearFinished()
        val graph = (application as DaymarkApplication).graph
        graph.resultNotifications.refreshStatus()
        graph.requestRecovery()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    private fun route(intent: Intent) {
        if (intent.data?.scheme != "daymark") return
        if (intent.dataString == "daymark://today") dev.daymark.app.TodayIntents.request()
        else intent.dataString?.let(QuickCheckIntents::requestRoute)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) route(intent)
        val app = application as DaymarkApplication
        detachResultNotifications = app.graph.resultNotifications.attach(this)
        val permissions = app.locationPermissions?.also { it.attach(this) }
            ?: AndroidLocationPermissions(this).also { app.locationPermissions = it }
        val googleAuth = app.googleAuth.also { it.attach(this) }
        setContent { DaymarkHost(permissions, googleAuth) }
    }
}
