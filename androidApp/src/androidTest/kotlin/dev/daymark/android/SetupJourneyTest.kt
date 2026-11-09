package dev.daymark.android

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.app.AppGraph
import dev.daymark.app.SetupDraftLoad
import dev.daymark.attendance.*
import dev.daymark.auth.*
import dev.daymark.auth.NativeAuthReply
import dev.daymark.auth.NativeAuthStatus
import dev.daymark.auth.NativeGoogleAuth
import dev.daymark.data.*
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.openAndroidDatabase
import dev.daymark.location.*
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.reminders.ReminderPort
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import dev.daymark.widgets.WidgetSink
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class SetupJourneyTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "setup-ui-${UUID.randomUUID()}.db"
    private lateinit var db: DaymarkDatabase
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-27T08:00:00Z")
    }
    private var fontScale by mutableStateOf<Float?>(null)
    private var permitted = false
    private var googleCalls = 0
    private var locationCalls = 0
    private var shown by mutableStateOf(true)
    private var graph by mutableStateOf<AppGraph?>(null)
    private val permissions = object : LocationPermissionPort {
        override fun current() = LocationCapabilities(permitted, false, permitted, true)
        override suspend fun requestForeground() = current()
        override suspend fun requestBackground() = current()
        override fun openSystemSettings() = Unit
    }
    private val location = object : LocationGateway {
        override suspend fun requestFix(budget: Duration): LocationResult {
            locationCalls++
            return LocationResult.Fix(0.0, 0.0, 10.0, clock.now(), clock.now(), Duration.ZERO)
        }
    }

    private fun graph(): AppGraph {
        return AppGraph(module {
            single<DaymarkDatabase> { db }
            single<Clock> { clock }
            single<LocationGateway> { location }
            single<dev.daymark.location.PlaceGeocoder> { dev.daymark.location.PlaceGeocoder { null } }
            single<LocationPermissionPort> { permissions }
            single<NativeGoogleAuth> {
                object : NativeGoogleAuth {
                    override fun authorize(
                        interactive: Boolean,
                        completion: (NativeAuthReply) -> Unit
                    ) {
                        googleCalls++; completion(
                            NativeAuthReply(
                                NativeAuthStatus.NEEDS_INTERACTION,
                                null,
                                null
                            )
                        )
                    }

                    override fun forget(completion: (Boolean) -> Unit) {
                        completion(true)
                    }
                }
            }
            single<ReminderPort> {
                object : ReminderPort {
                    override fun requestPermission(completion: (Boolean) -> Unit) {
                        completion(false)
                    }

                    override fun replace(remindersJson: String, completion: (Boolean) -> Unit) {
                        completion(false)
                    }

                    override fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit) {
                        completion(true)
                    }
                }
            }
            single<WidgetSink> {
                object : WidgetSink {
                    override fun publish(json: String) = false
                    override fun clear() = true
                }
            }
        })
    }

    @Before
    fun open() {
        db = openAndroidDatabase(context, name)
        graph = graph()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density.density,
                    fontScale ?: density.fontScale
                )
            ) {
                if (shown) graph?.let { App(it) }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("setup-next").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun close() {
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        graph?.close()
        db.close(); context.deleteDatabase(name)
    }

    private fun nextTo(tag: String) {
        compose.onNodeWithTag("setup-next").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun enterProfile() {
        nextTo("settings-language-system")
        nextTo("setup-name")
        compose.onNodeWithTag("setup-name").performScrollTo().performTextReplacement("Local test")
    }

    private fun enterPlace() {
        enterProfile()
        nextTo("setup-place-name")
        compose.onNodeWithTag("setup-place-name").performScrollTo()
            .performTextReplacement("Test place")
    }

    private fun seedLanguage(language: LanguagePreference) {
        runBlocking { graph!!.settings.update(language = language) }
        compose.waitForIdle()
    }

    private fun reviewSetup() {
        permitted = true
        seedLanguage(LanguagePreference.EN)
        enterPlace()
        compose.onNodeWithTag("setup-register-place").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("setup-place-ready").fetchSemanticsNodes().isNotEmpty()
        }
        nextTo("setup-grace")
        nextTo("setup-start")
    }

    @Test
    fun denied_location_keeps_arabic_rtl_draft_after_reopen_without_creating_a_tracker() {
        seedLanguage(LanguagePreference.AR)
        enterProfile()
        compose.onNodeWithTag("setup-name").performTextReplacement("أحمد / Ahmed")
        val field = compose.onNodeWithTag("setup-name").fetchSemanticsNode().boundsInRoot
        val label =
            compose.onNodeWithText("اسمك", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(
            "Arabic field label must align to the right",
            label.center.x > field.center.x
        )
        nextTo("setup-place-name")
        compose.onNodeWithTag("setup-place-name").performScrollTo().performTextReplacement("المكتب")
        compose.onNodeWithTag("setup-register-place").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("اسمح بالموقع الدقيق لتسجيل المكان.").fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("اسمح بالموقع الدقيق لتسجيل المكان.").assertIsDisplayed()
        runBlocking {
            dev.daymark.data.local.setup.SetupService(db, clock).observeDraft()
                .first { it is SetupDraftLoad.Loaded && it.draft.displayName == "أحمد / Ahmed" }
        }
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        db.close(); db = openAndroidDatabase(context, name)
        compose.runOnIdle { graph = graph(); shown = true }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("setup-place-name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("setup-place-name").assertTextContains("المكتب")
        compose.onNodeWithTag("setup-back").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("setup-name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("setup-name").performScrollTo().assertTextContains("أحمد / Ahmed")
        runBlocking { Assert.assertNull(db.dao().tracker()) }
        Assert.assertEquals(0, googleCalls)
    }

    @Test
    fun review_edit_actions_have_localized_accessible_names() {
        reviewSetup()
        for (section in listOf("Your profile", "Your place", "Your weekly schedule")) {
            compose.onNodeWithContentDescription("Edit $section").assertHasClickAction()
        }
        seedLanguage(LanguagePreference.AR)
        for (section in listOf("ملفك الشخصي", "مكانك", "جدولك الأسبوعي")) {
            compose.onNodeWithContentDescription("تعديل $section").assertHasClickAction()
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("تعديل ملفك الشخصي").performScrollTo()
                .performClick()
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag("setup-name").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("تعديل مكانك").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun large_text_navigation_labels_fit_in_both_languages() {
        enterProfile()
        compose.runOnIdle { fontScale = 2f }
        for ((language, labels) in listOf(
            LanguagePreference.EN to listOf("Back", "Next"),
            LanguagePreference.AR to listOf("السابق", "التالي")
        )) {
            seedLanguage(language)
            for (label in labels) {
                val text = compose.onNodeWithText(label, useUnmergedTree = true)
                text.assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                Assert.assertFalse(layouts.single().hasVisualOverflow)
            }
        }
    }

    @Test
    fun valid_setup_can_skip_google_and_notifications_and_uses_real_local_storage() {
        reviewSetup()
        compose.onNodeWithTag("setup-start").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("today-root").fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking {
            val tracker = requireNotNull(db.dao().tracker())
            Assert.assertEquals("Local test", tracker.displayName)
            Assert.assertNull(db.dao().binding(tracker.id))
            Assert.assertTrue(db.dao().days(tracker.id).isEmpty())
        }
        Assert.assertEquals(0, googleCalls)
    }

    @Test
    fun customize_opens_settings_once_and_cold_launch_returns_to_today() {
        reviewSetup()
        compose.onNodeWithTag("setup-customize").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("settings-name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("settings-widget-guidance").performScrollTo().assertIsDisplayed()
        Assert.assertEquals(0, googleCalls)
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        db.close(); db = openAndroidDatabase(context, name)
        compose.runOnIdle { graph = graph(); shown = true }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("today-root").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("setup-next").assertDoesNotExist()
    }

    @Test
    fun captured_preview_and_radius_survive_a_failed_recapture() {
        seedLanguage(LanguagePreference.EN)
        permitted = true
        enterPlace()
        compose.onNodeWithTag("setup-register-place").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("setup-place-coordinates").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("setup-place-coordinates")
            .assertTextContains("0.00000", substring = true)
        compose.onNodeWithTag("setup-radius").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(200f) }
        compose.runOnIdle { permitted = false }
        compose.onNodeWithTag("setup-register-place").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("setup-error").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("setup-place-coordinates").assertExists()
        val draft = runBlocking {
            graph!!.setup.observeDraft().first {
                it is SetupDraftLoad.Loaded && it.draft.place?.radiusMetres == 200
            }
        } as SetupDraftLoad.Loaded
        Assert.assertEquals(RegisteredPlace(0.0, 0.0, 200, 1), draft.draft.place)
        Assert.assertEquals(1, locationCalls)
    }

    @Test
    fun onboarding_preferences_apply_and_resume_before_profile() {
        nextTo("settings-language-ar")
        compose.onNodeWithTag("settings-language-ar").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("settings-language-ar") and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("setup-heading").assertTextEquals("التفضيلات")
        compose.onNodeWithTag("settings-theme-dark").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("settings-theme-dark") and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        graph?.close()
        db.close()
        db = openAndroidDatabase(context, name)
        compose.runOnIdle { graph = graph(); shown = true }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("settings-language-ar").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("settings-language-ar").assertIsSelected()
        compose.onNodeWithTag("settings-theme-dark").performScrollTo().assertIsSelected()
        nextTo("setup-name")
        runBlocking {
            Assert.assertEquals(LanguagePreference.AR, graph!!.settings.observe().first().language)
            Assert.assertEquals(ThemePreference.DARK, graph!!.settings.observe().first().theme)
            Assert.assertNull(db.dao().tracker())
        }
    }

    @Test
    fun settings_preferences_persist_after_relaunch() {
        compose.onNodeWithTag("language-en").assertDoesNotExist()
        compose.onNodeWithTag("theme-dark").assertDoesNotExist()
        reviewSetup()
        compose.onNodeWithTag("setup-customize").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("settings-language-ar").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("settings-language-ar").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("settings-language-ar") and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("settings-theme-dark").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("settings-theme-dark") and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        db.close(); db = openAndroidDatabase(context, name)
        compose.runOnIdle { graph = graph(); shown = true }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("today-root").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-language-ar").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("settings-theme-dark").performScrollTo().assertIsSelected()
        runBlocking {
            val saved = graph!!.settings.observe().first()
            Assert.assertEquals(LanguagePreference.AR, saved.language)
            Assert.assertEquals(ThemePreference.DARK, saved.theme)
        }
    }

    @Test
    fun radius_and_grace_have_localized_accessible_names_and_units() {
        permitted = true
        seedLanguage(LanguagePreference.EN)
        enterPlace()
        compose.onNodeWithTag("setup-register-place").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("setup-place-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("setup-radius").performScrollTo()
            .assertContentDescriptionEquals("Allowed radius")
        val radius =
            compose.onNodeWithTag("setup-radius")
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        Assert.assertTrue(radius.contains("100") && radius.contains("m"))
        seedLanguage(LanguagePreference.AR)
        compose.onNodeWithTag("setup-radius").performScrollTo()
            .assertContentDescriptionEquals("نطاق التحقق")
        val arabicRadius =
            compose.onNodeWithTag("setup-radius")
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        Assert.assertTrue(arabicRadius.contains("١٠٠") && arabicRadius.contains("م"))
        nextTo("setup-grace")
        compose.onNodeWithTag("setup-grace").performScrollTo()
            .assertContentDescriptionEquals("مهلة السماح")
        val arabicGrace =
            compose.onNodeWithTag("setup-grace")
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        Assert.assertTrue(arabicGrace.contains("٥"))
        seedLanguage(LanguagePreference.EN)
        compose.onNodeWithTag("setup-grace").performScrollTo()
            .assertContentDescriptionEquals("Grace period")
        val grace =
            compose.onNodeWithTag("setup-grace")
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        Assert.assertTrue(grace.contains("5") && grace.contains("minutes"))
    }

    @Test
    fun closed_storage_shows_an_error_instead_of_setup_or_creating_another_profile() {
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        db.close()
        compose.runOnIdle { shown = true }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("app-storage-error").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("setup-name").assertDoesNotExist()
    }

    @Test
    fun arabic_large_text_keeps_registration_and_its_error_readable() {
        seedLanguage(LanguagePreference.AR)
        enterPlace()
        compose.runOnIdle { fontScale = 2f }
        compose.onNodeWithTag("setup-register-place").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("اسمح بالموقع الدقيق لتسجيل المكان.").fetchSemanticsNodes()
                .isNotEmpty()
        }
        val error = compose.onNodeWithTag("setup-error", useUnmergedTree = true)
        error.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        error.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        Assert.assertFalse(layouts.single().hasVisualOverflow)
        Assert.assertTrue(
            "Entire error must fit in the visible area",
            error.fetchSemanticsNode().boundsInRoot.height + 2f >= layouts.single().size.height
        )
    }

    @Test
    fun welcome_is_reachable_in_both_languages_and_themes_at_large_text() {
        compose.runOnIdle { fontScale = 2f }
        for (language in listOf(LanguagePreference.EN, LanguagePreference.AR)) {
            for (theme in listOf(ThemePreference.LIGHT, ThemePreference.DARK)) {
                runBlocking { graph!!.settings.update(language = language, theme = theme) }
                compose.waitForIdle()
                compose.onNodeWithTag("setup-next").assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithTag("setup-heading").performScrollTo().assertIsDisplayed()
                val capture =
                    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                val output =
                    java.io.File(
                        context.getExternalFilesDir(null),
                        "onboarding-${language.name}-${theme.name}.png"
                    )
                output.outputStream()
                    .use { capture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
