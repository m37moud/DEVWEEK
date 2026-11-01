package dev.daymark.reporting

import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SyncRetryPolicyTest {
    @Test fun retry_is_bounded_and_honors_server_delay() {
        val policy = SyncRetryPolicy(jitter = { Duration.ZERO })
        assertEquals(listOf(2,4,8,16,32,60).map { it.seconds },(0..5).map { policy.nextDelay(it,null) })
        assertNull(policy.nextDelay(6,null))
        assertTrue(policy.nextDelay(0,120.seconds)!! >= 120.seconds)
        assertEquals(2.seconds,policy.nextDelay(0,(-2).seconds))
        val noisy = SyncRetryPolicy(jitter = { 1.seconds })
        assertEquals(3.seconds,noisy.nextDelay(0,null))
    }
}
