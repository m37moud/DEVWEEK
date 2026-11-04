package dev.daymark.shared

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacLocationExecutableTest {
    @Test
    fun direct_ide_launch_finds_the_built_helper_without_gradle_launch_properties() {
        val previous = System.clearProperty("compose.application.resources.dir")
        try {
            val helper = macLocationExecutable()
            assertTrue(Files.isExecutable(helper), "The runtime classpath must include the executable helper")
            assertTrue(Files.isRegularFile(helper.parent.parent.resolve("Info.plist")))
        } finally {
            if (previous != null) System.setProperty("compose.application.resources.dir", previous)
        }
    }

    @Test
    fun packaged_launch_uses_the_distribution_resources() {
        val previous = System.setProperty("compose.application.resources.dir", "/packaged/resources")
        try {
            assertEquals(
                Path.of("/packaged/resources/Daymark Location.app/Contents/MacOS/DaymarkLocation"),
                macLocationExecutable()
            )
        } finally {
            if (previous == null) System.clearProperty("compose.application.resources.dir")
            else System.setProperty("compose.application.resources.dir", previous)
        }
    }
}
