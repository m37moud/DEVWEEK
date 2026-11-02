package dev.daymark.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.auth.*
import dev.daymark.settings.LanguagePreference
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GoogleConnectionJourneyTest {
    @get:Rule val compose=createComposeRule()
    @Test fun cancelled_google_consent_preserves_local_tracker_and_unconfirmed_deletion_does_nothing() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="google-journey-${UUID.randomUUID()}.db"
        var consent=0
        val fixture=AttendanceTestApp(context,name,nativeAuth=object : NativeGoogleAuth {
            override fun authorize(interactive: Boolean,completion: (NativeAuthReply)->Unit) {
                Assert.assertTrue(interactive); consent++; completion(NativeAuthReply(NativeAuthStatus.CANCELLED,null,null))
            }
            override fun forget(completion: (Boolean)->Unit) { completion(true) }
        })
        var shown by mutableStateOf(true)
        try {
            runBlocking { fixture.graph.settings.update(language=LanguagePreference.EN) }
            compose.setContent { if (shown) App(fixture.graph) }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("nav-settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-settings").performClick()
            compose.onNodeWithTag("report-connect").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Google connection was cancelled. Your local records are kept.").fetchSemanticsNodes().isNotEmpty() }
            Assert.assertEquals(1,consent)
            Assert.assertNotNull(runBlocking { fixture.db.dao().tracker() })
            compose.onNodeWithTag("delete-local-data").performScrollTo().performClick()
            compose.onNodeWithText("Cancel").performClick()
            Assert.assertNotNull(runBlocking { fixture.db.dao().tracker() })
            Assert.assertEquals(0,fixture.locationRequests)
            compose.onNodeWithTag("delete-local-data").performScrollTo().performClick()
            compose.onNodeWithTag("report-confirm-action").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("setup-name").fetchSemanticsNodes().isNotEmpty() }
            Assert.assertNull(runBlocking { fixture.db.dao().tracker() })
        } finally {
            compose.runOnIdle { shown=false }; compose.waitForIdle()
            fixture.graph.reporting.close(); fixture.db.close(); context.deleteDatabase(name)
        }
    }
}
