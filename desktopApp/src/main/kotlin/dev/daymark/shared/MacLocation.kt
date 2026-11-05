package dev.daymark.shared

import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal class MacLocation(private val executable: Path) : LocationGateway, LocationPermissionPort {
    override fun current(): LocationCapabilities = capabilities(nativeReply("status", 2.seconds))

    override suspend fun requestForeground(): LocationCapabilities = runInterruptible(Dispatchers.IO) {
        capabilities(nativeReply("authorize", 30.seconds))
    }

    override suspend fun requestBackground() = current()

    override fun openSystemSettings() {
        nativeReply("settings", 2.seconds)
    }

    override suspend fun requestFix(budget: Duration): LocationResult = runInterruptible(Dispatchers.IO) {
        macLocationReply(nativeReply("locate", budget.coerceAtMost(30.seconds)), Clock.System.now())
    }

    private fun nativeReply(command: String, budget: Duration): String {
        if (budget <= Duration.ZERO) return "ERROR\tTIMEOUT"
        val process = try {
            ProcessBuilder(executable.toString(), command)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (_: IOException) {
            return "ERROR\tUNAVAILABLE"
        }
        try {
            if (!process.waitFor(budget.inWholeMilliseconds.coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
                return "ERROR\tTIMEOUT"
            }
            if (process.exitValue() != 0) return "ERROR\tUNAVAILABLE"
            return process.inputStream.bufferedReader().use { it.readText() }
        } finally {
            process.destroyForcibly()
            process.inputStream.close()
        }
    }

    private fun capabilities(reply: String): LocationCapabilities {
        val fields = reply.trim().split('\t')
        if (fields.size != 4 || fields[0] != "CAP" || fields.drop(1).any { it !in setOf("0", "1") }) {
            return LocationCapabilities(false, false, false, false)
        }
        return LocationCapabilities(fields[1] == "1", false, fields[2] == "1", fields[3] == "1")
    }
}

private const val MAC_LOCATION_HELPER = "Daymark Location.app/Contents/MacOS/DaymarkLocation"

internal fun macLocationExecutable(): Path {
    val resources = System.getProperty("compose.application.resources.dir")
    if (!resources.isNullOrBlank()) return Path.of(resources).resolve(MAC_LOCATION_HELPER)
    val helper = requireNotNull(MacLocation::class.java.getResource("/$MAC_LOCATION_HELPER")) {
        "The desktop runtime is missing its native location helper. Rebuild desktopApp."
    }
    return Path.of(helper.toURI())
}
