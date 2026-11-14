package dev.daymark.widgets

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

class WidgetCheckLocalizationTest {
    @Test
    fun android_only_check_feedback_has_matching_english_and_arabic_templates() {
        val shared = listOf(Path.of("."), Path.of("shared"))
            .first { Files.isDirectory(it.resolve("src/androidMain/res")) }

        fun strings(folder: String): Map<String, String> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(
                    shared.resolve("src/androidMain/res/$folder/widget_check_strings.xml").toFile()
                )
            val nodes = document.getElementsByTagName("string")
            return (0 until nodes.length).associate {
                val element = nodes.item(it) as Element
                element.getAttribute("name") to element.textContent
            }
        }

        val english = strings("values")
        val arabic = strings("values-ar")
        assertTrue(english.isNotEmpty())
        assertEquals(english.keys, arabic.keys)
        for (key in english.keys) {
            assertTrue(arabic.getValue(key).isNotBlank())
            val placeholder = "%1" + '$' + "s"
            assertEquals(
                english.getValue(key).split(placeholder).size,
                arabic.getValue(key).split(placeholder).size, key
            )
        }
    }
}
