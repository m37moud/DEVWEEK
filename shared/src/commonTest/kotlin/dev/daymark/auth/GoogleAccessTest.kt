package dev.daymark.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GoogleAccessTest {
    @Test
    fun configuration_failure_is_preserved_and_unknown_native_errors_are_sanitized() = runTest {
        for (key in listOf("authorization_configuration", "private_native_detail")) {
            val native = Native()
            val pending = async { GoogleAccess(native).accessToken(true) }
            runCurrent()
            native.completion!!(NativeAuthReply(NativeAuthStatus.FAILED, null, key))
            val expected = if (key == "authorization_configuration") key else "authorization_failed"
            assertEquals(expected, assertIs<AccessResult.Failed>(pending.await()).errorKey)
        }
    }

    private class Native : NativeGoogleAuth {
        var interactive: Boolean? = null
        var completion: ((NativeAuthReply) -> Unit)? = null
        override fun authorize(interactive: Boolean, completion: (NativeAuthReply) -> Unit) {
            this.interactive = interactive; this.completion = completion
        }

        override fun forget(completion: (Boolean) -> Unit) {
            completion(true)
        }
    }

    @Test
    fun silent_access_cannot_launch_consent() = runTest {
        val native = Native()
        val pending = async { GoogleAccess(native).accessToken(false) }
        runCurrent()
        assertEquals(false, native.interactive)
        native.completion!!(NativeAuthReply(NativeAuthStatus.NEEDS_INTERACTION, null, null))
        assertIs<AccessResult.NeedsInteraction>(pending.await())
        assertEquals(setOf("https://www.googleapis.com/auth/drive.file"), GOOGLE_REPORT_SCOPES)
    }

    @Test
    fun duplicate_callback_preserves_first_result_and_redacts_token() = runTest {
        val native = Native()
        val pending = async { GoogleAccess(native).accessToken(true) }
        runCurrent()
        val reply = NativeAuthReply(NativeAuthStatus.GRANTED, "secret-access-token", null)
        native.completion!!(reply)
        native.completion!!(NativeAuthReply(NativeAuthStatus.FAILED, null, "native_failed"))
        val result = assertIs<AccessResult.Granted>(pending.await())
        assertEquals("secret-access-token", result.token)
        assertFalse(result.toString().contains("secret-access-token"))
        assertFalse(reply.toString().contains("secret-access-token"))
    }

    @Test
    fun cancellation_remains_cancellation_and_late_callback_is_safe() = runTest {
        val native = Native()
        val pending = async { GoogleAccess(native).accessToken(true) }
        runCurrent(); pending.cancel()
        native.completion!!(NativeAuthReply(NativeAuthStatus.GRANTED, "late-secret", null))
        assertTrue(pending.isCancelled)
    }

    @Test
    fun forget_fences_an_older_authorization_callback() = runTest {
        val native = Native();
        val access = GoogleAccess(native)
        val pending = async { access.accessToken(true) }
        runCurrent(); access.forget()
        native.completion!!(NativeAuthReply(NativeAuthStatus.GRANTED, "obsolete-token", null))
        assertIs<AccessResult.NeedsInteraction>(pending.await())
    }

    @Test
    fun empty_token_is_failure_and_native_cancel_is_distinct() = runTest {
        for ((reply, cancelled) in listOf(
            NativeAuthReply(NativeAuthStatus.GRANTED, "", null) to false,
            NativeAuthReply(NativeAuthStatus.CANCELLED, null, null) to true
        )) {
            val native = Native();
            val pending = async { GoogleAccess(native).accessToken(true) }
            runCurrent(); native.completion!!(reply)
            if (cancelled) assertIs<AccessResult.Cancelled>(pending.await())
            else assertIs<AccessResult.Failed>(pending.await())
        }
    }

    @Test
    fun failed_native_forget_does_not_claim_session_was_cleared() = runTest {
        val native = object : NativeGoogleAuth {
            override fun authorize(interactive: Boolean, completion: (NativeAuthReply) -> Unit) {}
            override fun forget(completion: (Boolean) -> Unit) {
                completion(false)
            }
        }
        assertFailsWith<IllegalStateException> { GoogleAccess(native).forget() }
    }
}
