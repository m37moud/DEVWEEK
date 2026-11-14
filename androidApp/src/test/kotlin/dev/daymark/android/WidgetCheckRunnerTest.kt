package dev.daymark.android

import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.attendance.CheckRejection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class WidgetCheckRunnerTest {
    @Test
    fun publication_failure_does_not_replace_acknowledged_attendance() = runTest {
        val acknowledged = CheckOutcome.AlreadyRecorded(null)
        val runner = WidgetCheckRunner({ acknowledged }, { error("test-only publication failure") })
        assertSame(acknowledged, runner.run("tracker"))
        assertSame(acknowledged, WidgetCheckState.updates.value?.outcome)
    }

    @Test
    fun cancelling_refresh_preserves_acknowledged_attendance() = runTest {
        val started = CompletableDeferred<Unit>()
        val acknowledged = CheckOutcome.AlreadyRecorded(null)
        val runner =
            WidgetCheckRunner({ acknowledged }, { started.complete(Unit); awaitCancellation() })
        val pending = async { runner.run("tracker") }
        started.await()
        pending.cancelAndJoin()
        WidgetCheckState.cancelled("tracker")
        assertSame(acknowledged, WidgetCheckState.updates.value?.outcome)
    }

    @Test
    fun storage_failure_is_visible_and_retry_is_explicit() = runTest {
        var requests = 0
        val runner = WidgetCheckRunner({ requests++; error("test-only storage failure") }, {})
        assertEquals(
            CheckRejection.LOCAL_SAVE_FAILED,
            assertIs<CheckOutcome.Rejected>(runner.run("tracker")).reason
        )
        assertEquals(1, requests)
        assertEquals(
            CheckRejection.LOCAL_SAVE_FAILED,
            WidgetCheckState.updates.value?.outcome?.reason
        )
    }

    @Test
    fun cancellation_clears_checking_without_restarting() = runTest {
        val started = CompletableDeferred<Unit>()
        var requests = 0
        val runner =
            WidgetCheckRunner({ requests++; started.complete(Unit); awaitCancellation() }, {})
        val pending = async { runner.run("tracker") }
        started.await()
        assertNull(WidgetCheckState.updates.value?.outcome)
        pending.cancelAndJoin()
        assertEquals(CheckRejection.CANCELLED, WidgetCheckState.updates.value?.outcome?.reason)
        assertEquals(1, requests)
    }
}
