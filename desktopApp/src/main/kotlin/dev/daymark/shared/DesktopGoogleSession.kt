package dev.daymark.shared

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class DesktopOAuthClient(val id: String)

/** Missing, invalid, or unreadable local configuration requires desktop OAuth setup. */
internal fun loadDesktopOAuthClient(
    path: Path = Path.of(System.getProperty("user.home"), ".daymark", "google-oauth-client.json")
): DesktopOAuthClient? {
    return try {
        if (!Files.exists(path)) return null
        require(Files.size(path) <= 65536)
        val installed =
            Json.parseToJsonElement(Files.readString(path)).jsonObject["installed"]?.jsonObject
                ?: return null
        val id = installed["client_id"]?.jsonPrimitive?.content ?: return null
        id.takeIf { it.matches(Regex("[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com")) }
            ?.let(::DesktopOAuthClient)
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal interface DesktopCredentialStore {
    fun read(): String?
    fun write(session: String)
    fun clear()
}

internal fun desktopCredentialStore(): DesktopCredentialStore =
    if (System.getProperty("os.name")
            .startsWith("Mac")
    ) MacGoogleSession() else MemoryGoogleSession()

// shortcut: non-macOS sessions last only until exit; add native vaults before persistent desktop delivery.
internal class MemoryGoogleSession : DesktopCredentialStore {
    private var session: String? = null
    override fun read() = session
    override fun write(session: String) {
        this.session = session
    }

    override fun clear() {
        session = null
    }
}

private class MacGoogleSession : DesktopCredentialStore {
    override fun read() = command("read").takeIf { it.isNotBlank() }
    override fun write(session: String) {
        command("write", session)
    }

    override fun clear() {
        command("clear")
    }

    private fun command(action: String, session: String = ""): String {
        val executable = macLocationExecutable().resolveSibling("DaymarkGoogleSession")
        val process = ProcessBuilder(executable.toString(), action)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val output = FutureTask { process.inputStream.bufferedReader().use { it.readText() } }
        val reader = Thread.ofVirtual().start(output)
        try {
            process.outputStream.bufferedWriter().use { it.write(session) }
            check(
                process.waitFor(
                    30,
                    TimeUnit.SECONDS
                ) && process.exitValue() == 0
            ) { "session_unavailable" }
            return output.get(1, TimeUnit.SECONDS)
        } finally {
            process.destroyForcibly()
            reader.interrupt()
        }
    }
}
