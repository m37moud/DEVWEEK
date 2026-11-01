package dev.daymark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.daymark.app.*
import dev.daymark.data.TrackerEntity
import dev.daymark.resources.*
import dev.daymark.settings.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.*

@Serializable data object SetupRoute : NavKey
@Serializable data object TodayRoute : NavKey
@Serializable data class HistoryRoute(val year: Int, val month: Int) : NavKey
private val navigationConfig = SavedStateConfiguration {
    serializersModule = SerializersModule { polymorphic(NavKey::class) {
        subclass(SetupRoute::class, SetupRoute.serializer()); subclass(TodayRoute::class, TodayRoute.serializer()); subclass(HistoryRoute::class, HistoryRoute.serializer())
    } }
}
@Composable fun AppNavigation(graph: AppGraph, tracker: TrackerEntity?, settings: AppSettings) {
    val stack = rememberNavBackStack(navigationConfig, if (tracker == null) SetupRoute else TodayRoute)
    LaunchedEffect(tracker?.id) {
        val destination = if (tracker == null) SetupRoute else TodayRoute
        if ((tracker == null && stack.lastOrNull() != SetupRoute) || (tracker != null && stack.lastOrNull() == SetupRoute)) { stack.clear(); stack.add(destination) }
    }
    LaunchedEffect(graph, tracker?.id) { QuickCheckIntents.events.collect {
        val current = if (tracker == null) SetupRoute else TodayRoute
        if (stack.lastOrNull() != current) { stack.clear(); stack.add(current) }
    } }
    NavDisplay(backStack = stack, onBack = { if (stack.size > 1) stack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<SetupRoute> {
                val model = viewModel { SetupViewModel(graph.setup, graph.location, graph.permissions, graph.clock) }
                SetupScreen(model, graph, settings)
            }
            entry<TodayRoute> {
                val model = viewModel { TodayViewModel(graph.repository, graph.attendance, graph.planReader, graph.permissions, graph.clock) }
                TodayScreen(model, tracker?.displayName.orEmpty(), onHistory = { model.uiState.value.date?.let { stack.add(HistoryRoute(it.year, it.month.number)) } })
            }
            entry<HistoryRoute> { route ->
                val model = viewModel { HistoryViewModel(graph.repository, LocalDate(route.year, route.month, 1)) }
                HistoryScreen(model, onBack = { stack.removeLastOrNull() }, onEditTimeOff = { stack.removeLastOrNull() })
            }
        })
}
