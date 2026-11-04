package dev.daymark.shared

import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationFailure
import dev.daymark.location.LocationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MacLocationTest {
    @Test
    fun permissions_are_refreshed_after_a_system_settings_change() = withHelper { helper, directory ->
        val status = directory.resolve("status")
        status.writeText("CAP\t0\t0\t1")
        helper.writeText("#!/bin/sh\ncat '${status}'\n")
        val location = MacLocation(helper)
        assertFalse(location.current().foregroundGranted)
        status.writeText("CAP\t1\t1\t1")
        assertEquals(LocationCapabilities(true, false, true, true), location.current())
        runBlocking { assertTrue(location.requestForeground().foregroundGranted) }
    }

    @Test
    fun a_hung_native_request_times_out() = withHelper { helper, _ ->
        helper.writeText("#!/bin/sh\nexec /bin/sleep 60\n")
        runBlocking {
            assertEquals(
                LocationResult.Failure(LocationFailure.TIMEOUT),
                MacLocation(helper).requestFix(50.milliseconds)
            )
        }
    }

    @Test
    fun cancelling_a_request_terminates_the_native_process() = withHelper { helper, directory ->
        val pidFile = directory.resolve("pid")
        helper.writeText("#!/bin/sh\necho $$ > '${pidFile}'\nexec /bin/sleep 60\n")
        runBlocking {
            val request = async(Dispatchers.Default) { MacLocation(helper).requestFix(30.seconds) }
            withTimeout(5.seconds) { while (!Files.exists(pidFile)) delay(10) }
            val pid = Files.readString(pidFile).trim().toLong()
            request.cancelAndJoin()
            withTimeout(5.seconds) { while (ProcessHandle.of(pid).map { it.isAlive }.orElse(false)) delay(10) }
        }
    }

    @Test
    fun a_missing_native_helper_is_unavailable() = runBlocking {
        val directory = Files.createTempDirectory("daymark-missing")
        try {
            assertEquals(
                LocationResult.Failure(LocationFailure.UNAVAILABLE),
                MacLocation(directory.resolve("missing")).requestFix(1.seconds)
            )
        } finally {
            Files.delete(directory)
        }
    }

    private fun withHelper(check: (java.nio.file.Path, java.nio.file.Path) -> Unit) {
        val directory = Files.createTempDirectory("daymark-location-test")
        val helper = Files.createFile(directory.resolve("helper"))
        helper.toFile().setExecutable(true)
        try {
            check(helper, directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
