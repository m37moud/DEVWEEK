package dev.daymark.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.daymark.presentation.features.app.AppScreen
import dev.daymark.presentation.features.app.AppViewModel
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(graph: AppGraph) {
    val hostOwner = LocalViewModelStoreOwner.current
    val fallbackOwner = if (hostOwner == null) remember(graph) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    } else null
    DisposableEffect(fallbackOwner) { onDispose { fallbackOwner?.viewModelStore?.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides (hostOwner ?: fallbackOwner!!)) {
        KoinIsolatedContext(context = graph.container) {
            AppScreen(koinViewModel<AppViewModel>(), graph.platform)
        }
    }
}
