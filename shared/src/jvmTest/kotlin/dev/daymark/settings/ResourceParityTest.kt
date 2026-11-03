package dev.daymark.settings

import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceParityTest {
    @Test
    fun all_app_copy_has_english_arabic_and_matching_parameters() {
        val root = listOf(
            Path.of("src/commonMain/composeResources"),
            Path.of("shared/src/commonMain/composeResources")
        ).first { Files.isDirectory(it) }

        fun entries(language: String): Map<String, Element> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(root.resolve(language).resolve("strings.xml").toFile())
            val children = document.documentElement.childNodes
            return (0 until children.length).mapNotNull { children.item(it) as? Element }
                .associateBy { it.getAttribute("name") }
        }

        val en = entries("values");
        val ar = entries("values-ar")
        assertEquals(en.keys, ar.keys)
        assertTrue(
            en.keys.containsAll(
                setOf(
                    "app_name",
                    "setup_title",
                    "setup_name",
                    "location_denied",
                    "reminder_title",
                    "reminder_body"
                )
            )
        )
        val parameter = Regex("%([1-9][0-9]*)\\$[sd]")
        for ((key, english) in en) {
            val arabic = ar.getValue(key)
            assertTrue(arabic.textContent.isNotBlank(), key)
            assertEquals(
                parameter.findAll(english.textContent).map { it.groupValues[1] }.toSet(),
                parameter.findAll(arabic.textContent).map { it.groupValues[1] }.toSet(), key
            )
            if (english.tagName == "plurals") {
                val quantities = arabic.getElementsByTagName("item")
                assertEquals(
                    setOf("zero", "one", "two", "few", "many", "other"),
                    (0 until quantities.length).map {
                        (quantities.item(it) as Element).getAttribute(
                            "quantity"
                        )
                    }.toSet(), key
                )
            }
        }
    }
}
