package dev.daymark.android

import dev.daymark.auth.*
import kotlin.test.*

class AndroidGoogleAuthTest {
    private class Actions : AndroidAuthorizationActions {
        var scopes: Set<String> = emptySet()
        var completion: ((AndroidAuthAttempt) -> Unit)? = null
        var launches = 0
        var closes = 0
        var resolution: ((NativeAuthReply) -> Unit)? = null
        override fun request(scopes: Set<String>, completion: (AndroidAuthAttempt) -> Unit) {
            this.scopes=scopes; this.completion=completion
        }
        fun pending() = AndroidAuthAttempt.Resolution { complete -> launches++; resolution=complete }
        override fun reset(completion: (Boolean) -> Unit) { completion(true) }
        override fun close() { closes++ }
    }
    @Test fun silent_request_does_not_launch_resolution_and_requests_only_drive_file() {
        val actions=Actions(); val auth=AndroidGoogleAuth(actions)
        val replies=mutableListOf<NativeAuthReply>()
        auth.authorize(false,replies::add)
        actions.completion!!(actions.pending())
        assertEquals(0,actions.launches)
        assertEquals(setOf("https://www.googleapis.com/auth/drive.file"),actions.scopes)
        assertEquals(NativeAuthStatus.NEEDS_INTERACTION,replies.single().status)
    }
    @Test fun duplicate_resolution_and_completion_are_delivered_once() {
        val actions=Actions(); val auth=AndroidGoogleAuth(actions)
        val replies=mutableListOf<NativeAuthReply>()
        auth.authorize(true,replies::add)
        actions.completion!!(actions.pending()); actions.completion!!(actions.pending())
        assertEquals(1,actions.launches)
        actions.resolution!!(NativeAuthReply(NativeAuthStatus.GRANTED,"test-token",null))
        actions.resolution!!(NativeAuthReply(NativeAuthStatus.FAILED,null,"failed"))
        assertEquals(1,replies.size)
        assertEquals(NativeAuthStatus.GRANTED,replies.single().status)
    }
    @Test fun cancelled_native_consent_stays_cancelled() {
        val actions=Actions(); val auth=AndroidGoogleAuth(actions)
        val replies=mutableListOf<NativeAuthReply>()
        auth.authorize(true,replies::add)
        actions.completion!!(actions.pending())
        actions.resolution!!(NativeAuthReply(NativeAuthStatus.CANCELLED,null,null))
        assertEquals(NativeAuthStatus.CANCELLED,replies.single().status)
    }
    @Test fun forget_and_activity_rebind_fence_old_callbacks() {
        val actions=Actions(); val auth=AndroidGoogleAuth(actions)
        val replies=mutableListOf<NativeAuthReply>()
        auth.authorize(true,replies::add)
        auth.forget { assertTrue(it) }
        actions.completion!!(AndroidAuthAttempt.Ready(NativeAuthReply(NativeAuthStatus.GRANTED,"obsolete-token",null)))
        assertEquals(NativeAuthStatus.NEEDS_INTERACTION,replies.single().status)
        val other=Actions()
        replies.clear(); auth.authorize(true,replies::add); auth.bindActions(other)
        actions.completion!!(AndroidAuthAttempt.Ready(NativeAuthReply(NativeAuthStatus.GRANTED,"older-token",null)))
        assertEquals(NativeAuthStatus.NEEDS_INTERACTION,replies.single().status)
    }

    @Test fun destroyed_activity_leaves_silent_authorization_available_and_cannot_detach_new_host() {
        val old=Actions(); val background=Actions(); val auth=AndroidGoogleAuth(old)
        auth.detachActions(old,background)
        val replies=mutableListOf<NativeAuthReply>()
        auth.authorize(false,replies::add)
        background.completion!!(AndroidAuthAttempt.Ready(NativeAuthReply(NativeAuthStatus.GRANTED,"background-token",null)))
        assertEquals(NativeAuthStatus.GRANTED,replies.single().status)
        assertEquals(0,background.launches)
        assertEquals(1,old.closes)
        val newer=Actions(); val unused=Actions()
        auth.bindActions(newer); auth.detachActions(old,unused)
        auth.authorize(false) { }
        assertNotNull(newer.completion)
        assertEquals(1,unused.closes)
    }
}
