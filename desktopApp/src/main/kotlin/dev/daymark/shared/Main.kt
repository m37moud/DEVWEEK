package dev.daymark.shared

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.daymark.app.App
import dev.daymark.data.local.openJvmDatabase
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val directory = Files.createDirectories(Path.of(System.getProperty("user.home"), ".daymark"))
    val database = openJvmDatabase(directory.resolve("daymark.db").toString())
    val graph = desktopAppGraph(database)
    try {
        application(exitProcessOnExit = false) {
            Window(onCloseRequest = ::exitApplication, title = "Daymark",
                state = rememberWindowState(
                    position = WindowPosition(alignment = Alignment.TopEnd),
                    width = 350.dp , height = 750.dp
                )
            ) {
                App(graph)
            }
        }
    } finally {
        graph.close()
        database.close()
    }
}
