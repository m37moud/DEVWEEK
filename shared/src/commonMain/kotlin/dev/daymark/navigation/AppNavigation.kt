package dev.daymark.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.daymark.app.HostPlatform
import dev.daymark.app.QuickCheckIntents
import dev.daymark.app.QuickCheckRequest
import dev.daymark.models.TrackerSummary
import dev.daymark.presentation.component.label
import dev.daymark.presentation.features.app.AppUiEffect
import dev.daymark.presentation.features.app.AppViewModel
import dev.daymark.presentation.features.history.HistoryScreen
import dev.daymark.presentation.features.history.HistoryViewModel
import dev.daymark.presentation.features.plan.PlanScreen
import dev.daymark.presentation.features.plan.PlanViewModel
import dev.daymark.presentation.features.settings.SettingsScreen
import dev.daymark.presentation.features.settings.SettingsViewModel
import dev.daymark.presentation.features.setup.SetupScreen
import dev.daymark.presentation.features.setup.SetupViewModel
import dev.daymark.presentation.features.today.TodayScreen
import dev.daymark.presentation.features.today.TodayViewModel
import dev.daymark.resources.Res
import dev.daymark.resources.history
import dev.daymark.resources.plan
import dev.daymark.resources.settings
import dev.daymark.resources.today
import dev.daymark.settings.str
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
@SerialName("dev.daymark.ui.SetupRoute")
data object SetupRoute : NavKey

@Serializable
@SerialName("dev.daymark.ui.TodayRoute")
data object TodayRoute : NavKey

@Serializable
@SerialName("dev.daymark.ui.HistoryRoute")
data class HistoryRoute(val year: Int, val month: Int) : NavKey

@Serializable
@SerialName("dev.daymark.ui.PlanRoute")
data class PlanRoute(val timeOffDate: String? = null) : NavKey

@Serializable
@SerialName("dev.daymark.ui.SettingsRoute")
data object SettingsRoute : NavKey

enum class SetupFinish { TODAY, CUSTOMIZE }

internal val navigationConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(SetupRoute::class, SetupRoute.serializer()); subclass(
            TodayRoute::class,
            TodayRoute.serializer()
        )
            subclass(HistoryRoute::class, HistoryRoute.serializer()); subclass(
            PlanRoute::class,
            PlanRoute.serializer()
        )
            subclass(SettingsRoute::class, SettingsRoute.serializer())
        }
    }
}

@Composable
fun AppNavigation(appModel: AppViewModel, platform: HostPlatform, tracker: TrackerSummary?) {
    val stack =
        rememberNavBackStack(navigationConfig, if (tracker == null) SetupRoute else TodayRoute)
    var setupFinish by remember { mutableStateOf<SetupFinish?>(null) }
    var pendingCheck by remember { mutableStateOf<QuickCheckRequest?>(null) }
    fun go(route: NavKey) {
        if (route != TodayRoute) pendingCheck = null
        if (stack.lastOrNull() != route) {
            stack.clear(); stack.add(route)
        }
    }

    LaunchedEffect(appModel) {
        appModel.effects.collect { effect ->
            when (effect) {
                is AppUiEffect.History -> go(
                    HistoryRoute(
                        effect.date.year,
                        effect.date.month.number
                    )
                )
            }
        }
    }
    LaunchedEffect(tracker?.id) {
        if (tracker == null) {
            setupFinish = null
            go(SetupRoute)
        } else if (stack.lastOrNull() == SetupRoute) {
            go(if (setupFinish == SetupFinish.CUSTOMIZE) SettingsRoute else TodayRoute)
            setupFinish = null
        }
    }
    LaunchedEffect(appModel, tracker?.id) {
        QuickCheckIntents.events.collect { request ->
            go(if (tracker == null) SetupRoute else TodayRoute)
            pendingCheck =
                request.takeIf { tracker != null && (it.allowCurrentTracker || it.route != null) }
        }
    }
    Scaffold(bottomBar = {
        if (tracker != null) NavigationBar {
            val current = stack.lastOrNull()
            val labels =
                listOf(Res.string.today, Res.string.history, Res.string.plan, Res.string.settings)
            val tags = listOf("today", "history", "plan", "settings")
            val selected = when (current) {
                TodayRoute -> 0; is HistoryRoute -> 1; is PlanRoute -> 2; SettingsRoute -> 3; else -> -1
            }
            labels.forEachIndexed { index, label ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = {
                        when (index) {
                            0 -> go(TodayRoute); 1 -> appModel.showHistory(); 2 -> go(PlanRoute()); else -> go(
                            SettingsRoute
                        )
                        }
                    },
                    icon = { NavigationGlyph(index) },
                    label = { Text(str(label)) },
                    modifier = Modifier.testTag("nav-${tags[index]}")
                )
            }
        }
    }) { padding ->
        NavDisplay(
            backStack = stack,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            onBack = { if (stack.lastOrNull() != TodayRoute && tracker != null) go(TodayRoute) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<SetupRoute> {
                    val model = koinViewModel<SetupViewModel>()
                    SetupScreen(model, platform) { setupFinish = it }
                }
                entry<TodayRoute> {
                    val model = koinViewModel<TodayViewModel>()
                    val request = pendingCheck
                    LaunchedEffect(model, request?.token) {
                        if (request != null) {
                            model.uiState.first { it.ready && it.active }
                            model.requestQuickCheck(request.route?.trackerId)
                            if (pendingCheck?.token == request.token) pendingCheck = null
                        }
                    }
                    TodayScreen(
                        model,
                        tracker?.displayName.orEmpty(),
                        onHistory = appModel::showHistory
                    )
                }
                entry<HistoryRoute> { route ->
                    val model = koinViewModel<HistoryViewModel> {
                        parametersOf(LocalDate(route.year, route.month, 1))
                    }
                    HistoryScreen(
                        model,
                        onBack = { go(TodayRoute) },
                        onEditTimeOff = { date -> go(PlanRoute(date.toString())) })
                }
                entry<PlanRoute> { route ->
                    val model = koinViewModel<PlanViewModel>()
                    PlanScreen(model, model.clock, route.timeOffDate?.let(LocalDate::parse))
                }
                entry<SettingsRoute> {
                    val model = koinViewModel<SettingsViewModel>()
                    SettingsScreen(model, platform)
                }
            })
    }
}

@Composable
private fun NavigationGlyph(kind: Int) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(2.dp.toPx())
        val w = size.width;
        val h = size.height
        when (kind) {
            0 -> {
                drawRoundRect(
                    color,
                    Offset(w * .15f, h * .15f),
                    Size(w * .7f, h * .7f),
                    CornerRadius(w * .12f),
                    style = stroke
                )
                drawLine(color, Offset(w * .3f, h * .5f), Offset(w * .45f, h * .65f), stroke.width)
                drawLine(
                    color,
                    Offset(w * .45f, h * .65f),
                    Offset(w * .72f, h * .35f),
                    stroke.width
                )
            }

            1 -> {
                drawCircle(color, w * .36f, style = stroke)
                drawLine(color, center, Offset(w * .5f, h * .27f), stroke.width)
                drawLine(color, center, Offset(w * .68f, h * .6f), stroke.width)
            }

            2 -> {
                drawRoundRect(
                    color,
                    Offset(w * .15f, h * .2f),
                    Size(w * .7f, h * .65f),
                    CornerRadius(w * .08f),
                    style = stroke
                )
                drawLine(color, Offset(w * .15f, h * .4f), Offset(w * .85f, h * .4f), stroke.width)
                drawLine(color, Offset(w * .35f, h * .1f), Offset(w * .35f, h * .3f), stroke.width)
                drawLine(color, Offset(w * .65f, h * .1f), Offset(w * .65f, h * .3f), stroke.width)
            }

            else -> for (index in 0..2) {
                val y = h * (.25f + index * .25f);
                val x = if (index == 1) w * .65f else w * .35f
                drawLine(color, Offset(w * .15f, y), Offset(w * .85f, y), stroke.width)
                drawCircle(color, w * .1f, Offset(x, y))
            }
        }
    }
}
