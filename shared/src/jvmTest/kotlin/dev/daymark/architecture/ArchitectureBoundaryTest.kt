package dev.daymark.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureBoundaryTest {
    @Test
    fun presentation_uses_repository_interfaces_instead_of_storage_or_app_graph() {
        val sources = Path.of("src/commonMain/kotlin/dev/daymark")
        val presentation = sources.resolve("presentation")
        assertTrue(Files.isDirectory(presentation), "Feature presentation packages must exist")
        Files.walk(presentation).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.forEach { path ->
                val text = Files.readString(path)
                val forbidden = listOf(
                    "androidx.room",
                    "androidx.sqlite",
                    "dev.daymark.data.local",
                    "AppGraph",
                    "ReportingRuntime"
                )
                assertTrue(
                    forbidden.none(text::contains),
                    "Storage/composition dependency in $path"
                )
            }
        }
    }
}
