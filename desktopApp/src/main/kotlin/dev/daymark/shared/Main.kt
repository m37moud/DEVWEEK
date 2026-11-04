package dev.daymark.shared

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.daymark.app.App
import dev.daymark.data.openJvmDatabase
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val directory = Files.createDirectories(Path.of(System.getProperty("user.home"), ".daymark"))
    val database = openJvmDatabase(directory.resolve("daymark.db").toString())
    val graph = desktopAppGraph(database)
    try {
        application(exitProcessOnExit = false) {
            Window(onCloseRequest = ::exitApplication, title = "Daymark") {
                App(graph)
            }
        }
    } finally {
        graph.reporting.close()
        database.close()
    }
}
