package dev.daymark.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.*

@Serializable data object SetupRoute : NavKey
@Serializable data object TodayRoute : NavKey
@Serializable data class HistoryRoute(val year: Int,val month: Int) : NavKey
@Serializable data class PlanRoute(val timeOffDate: String? = null) : NavKey
@Serializable data object SettingsRoute : NavKey
private val navigationConfig = SavedStateConfiguration {
    serializersModule = SerializersModule { polymorphic(NavKey::class) {
        subclass(SetupRoute::class,SetupRoute.serializer()); subclass(TodayRoute::class,TodayRoute.serializer())
        subclass(HistoryRoute::class,HistoryRoute.serializer()); subclass(PlanRoute::class,PlanRoute.serializer())
        subclass(SettingsRoute::class,SettingsRoute.serializer())
    } }
}
@Composable fun AppNavigation(graph: AppGraph,tracker: TrackerEntity?,settings: AppSettings) {
    val stack = rememberNavBackStack(navigationConfig,if (tracker == null) SetupRoute else TodayRoute)
    val scope = rememberCoroutineScope()
    fun go(route: NavKey) { if (stack.lastOrNull() != route) { stack.clear(); stack.add(route) } }
    fun history() { scope.launch {
        val context = graph.planReader.context() ?: return@launch
        val date = graph.clock.now().toLocalDateTime(TimeZone.of(context.zoneId)).date
        go(HistoryRoute(date.year,date.month.number))
    } }
    LaunchedEffect(tracker?.id) {
        if (tracker == null) go(SetupRoute) else if (stack.lastOrNull() == SetupRoute) go(TodayRoute)
    }
    LaunchedEffect(graph,tracker?.id) { QuickCheckIntents.events.collect { go(if (tracker == null) SetupRoute else TodayRoute) } }
    Scaffold(bottomBar = {
        if (tracker != null) NavigationBar {
            val current = stack.lastOrNull()
            val labels = listOf(Res.string.today,Res.string.history,Res.string.plan,Res.string.settings)
            val tags = listOf("today","history","plan","settings")
            val selected = when(current) { TodayRoute -> 0; is HistoryRoute -> 1; is PlanRoute -> 2; SettingsRoute -> 3; else -> -1 }
            labels.forEachIndexed { index,label ->
                NavigationBarItem(selected = selected == index,onClick = {
                    when(index) { 0 -> go(TodayRoute); 1 -> history(); 2 -> go(PlanRoute()); else -> go(SettingsRoute) }
                },icon = { NavigationGlyph(index) },label = { Text(str(label)) },modifier = Modifier.testTag("nav-${tags[index]}"))
            }
        }
    }) { padding ->
        NavDisplay(backStack = stack,modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            onBack = { if (stack.lastOrNull() != TodayRoute && tracker != null) go(TodayRoute) },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(),rememberViewModelStoreNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<SetupRoute> {
                    val model = viewModel { SetupViewModel(graph.setup,graph.location,graph.permissions,graph.clock) }
                    SetupScreen(model,graph,settings)
                }
                entry<TodayRoute> {
                    val model = viewModel { TodayViewModel(graph.repository,graph.attendance,graph.planReader,graph.permissions,graph.clock) }
                    TodayScreen(model,tracker?.displayName.orEmpty(),onHistory = ::history)
                }
                entry<HistoryRoute> { route ->
                    val model = viewModel { HistoryViewModel(graph.repository,LocalDate(route.year,route.month,1)) }
                    HistoryScreen(model,onBack = { go(TodayRoute) },onEditTimeOff = { date -> go(PlanRoute(date.toString())) })
                }
                entry<PlanRoute> { route ->
                    val model = viewModel { PlanViewModel(graph.plans,graph.timeOff,graph.location,graph.permissions,graph.clock) }
                    PlanScreen(model,graph.clock,route.timeOffDate?.let(LocalDate::parse))
                }
                entry<SettingsRoute> {
                    val model = viewModel { SettingsViewModel(graph.db,graph.settings,graph.permissions) }
                    SettingsScreen(model,graph)
                }
            })
    }
}

@Composable private fun NavigationGlyph(kind: Int) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(2.dp.toPx())
        val w = size.width; val h = size.height
        when(kind) {
            0 -> {
                drawRoundRect(color,Offset(w*.15f,h*.15f),Size(w*.7f,h*.7f),CornerRadius(w*.12f),style = stroke)
                drawLine(color,Offset(w*.3f,h*.5f),Offset(w*.45f,h*.65f),stroke.width)
                drawLine(color,Offset(w*.45f,h*.65f),Offset(w*.72f,h*.35f),stroke.width)
            }
            1 -> {
                drawCircle(color,w*.36f,style = stroke)
                drawLine(color,center,Offset(w*.5f,h*.27f),stroke.width)
                drawLine(color,center,Offset(w*.68f,h*.6f),stroke.width)
            }
            2 -> {
                drawRoundRect(color,Offset(w*.15f,h*.2f),Size(w*.7f,h*.65f),CornerRadius(w*.08f),style = stroke)
                drawLine(color,Offset(w*.15f,h*.4f),Offset(w*.85f,h*.4f),stroke.width)
                drawLine(color,Offset(w*.35f,h*.1f),Offset(w*.35f,h*.3f),stroke.width)
                drawLine(color,Offset(w*.65f,h*.1f),Offset(w*.65f,h*.3f),stroke.width)
            }
            else -> for (index in 0..2) {
                val y = h*(.25f+index*.25f); val x = if (index == 1) w*.65f else w*.35f
                drawLine(color,Offset(w*.15f,y),Offset(w*.85f,y),stroke.width)
                drawCircle(color,w*.1f,Offset(x,y))
            }
        }
    }
}
