package dev.daymark.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.daymark.app.App

@Composable
internal fun DaymarkHost(permissions: AndroidLocationPermissions, googleAuth: AndroidGoogleAuth) {
    val app = LocalContext.current.applicationContext as DaymarkApplication
    App(app.graph.sharedGraph(permissions))
}
