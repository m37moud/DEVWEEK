package dev.daymark.architecture

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import dev.daymark.navigation.navigationConfig
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import dev.daymark.navigation.HistoryRoute
import dev.daymark.navigation.PlanRoute
import dev.daymark.navigation.SettingsRoute
import dev.daymark.navigation.SetupRoute
import dev.daymark.navigation.TodayRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
class NavigationCompatibilityTest {
    @Test
    fun moved_routes_keep_their_saved_names_and_arguments() {
        assertEquals("dev.daymark.ui.SetupRoute", SetupRoute.serializer().descriptor.serialName)
        assertEquals("dev.daymark.ui.TodayRoute", TodayRoute.serializer().descriptor.serialName)
        assertEquals("dev.daymark.ui.HistoryRoute", HistoryRoute.serializer().descriptor.serialName)
        assertEquals("dev.daymark.ui.PlanRoute", PlanRoute.serializer().descriptor.serialName)
        assertEquals(
            "dev.daymark.ui.SettingsRoute",
            SettingsRoute.serializer().descriptor.serialName
        )
        assertEquals(
            HistoryRoute(2026, 11),
            Json.decodeFromString(HistoryRoute.serializer(), "{\"year\":2026,\"month\":11}")
        )
        assertEquals(
            PlanRoute("2026-11-04"),
            Json.decodeFromString(PlanRoute.serializer(), "{\"timeOffDate\":\"2026-11-04\"}")
        )
    }

    @Serializable
    @SerialName("dev.daymark.ui.TodayRoute")
    private data object LegacyToday : NavKey

    @Serializable
    @SerialName("dev.daymark.ui.HistoryRoute")
    private data class LegacyHistory(val year: Int, val month: Int) : NavKey

    @Serializable
    @SerialName("dev.daymark.ui.PlanRoute")
    private data class LegacyPlan(val timeOffDate: String? = null) : NavKey

    @Test
    fun restores_a_legacy_polymorphic_back_stack_with_production_configuration() {
        val legacyConfig = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(LegacyToday::class, LegacyToday.serializer())
                    subclass(LegacyHistory::class, LegacyHistory.serializer())
                    subclass(LegacyPlan::class, LegacyPlan.serializer())
                }
            }
        }
        val serializer = NavBackStackSerializer(PolymorphicSerializer(NavKey::class))
        val legacy =
            NavBackStack<NavKey>(LegacyToday, LegacyHistory(2026, 11), LegacyPlan("2026-11-04"))
        val saved = encodeToSavedState(serializer, legacy, legacyConfig)
        val restored = decodeFromSavedState(serializer, saved, navigationConfig)
        assertEquals(
            listOf<NavKey>(TodayRoute, HistoryRoute(2026, 11), PlanRoute("2026-11-04")),
            restored.toList()
        )
    }
}
