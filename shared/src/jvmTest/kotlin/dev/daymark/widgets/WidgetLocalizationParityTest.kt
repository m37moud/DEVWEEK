package dev.daymark.widgets

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.test.*

class WidgetLocalizationParityTest {
    @Test fun native_status_copy_matches_shared_copy_in_english_and_arabic() {
        val shared = listOf(Path.of("."),Path.of("shared")).first { Files.isDirectory(it.resolve("src/commonMain/composeResources")) }
        val repo = if (shared == Path.of(".")) Path.of("..") else Path.of(".")
        fun xml(path: Path): Map<String,String> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile()).documentElement.childNodes
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { it.tagName == "string" }.associate { it.getAttribute("name") to it.textContent }
        }
        val ios = Json.parseToJsonElement(Files.readString(repo.resolve("iosApp/DaymarkWidget/Localizable.xcstrings"))).jsonObject.getValue("strings").jsonObject
        val mapping = mapOf("scheduled" to "status_scheduled","awaiting_check" to "status_awaiting","present" to "status_present","late" to "status_late",
            "absent" to "status_absent","day_off" to "status_day_off","holiday" to "status_holiday","vacation" to "status_vacation")
        for ((locale,folder) in listOf("en" to "values","ar" to "values-ar")) {
            val common = xml(shared.resolve("src/commonMain/composeResources/$folder/strings.xml"))
            val android = xml(shared.resolve("src/androidMain/res/$folder/widget_strings.xml"))
            for ((key,value) in mapping) assertEquals(common.getValue(value),android.getValue("widget_status_$key"))
            for ((key,value) in android) {
                val apple = ios.getValue(key).jsonObject.getValue("localizations").jsonObject.getValue(locale).jsonObject.getValue("stringUnit").jsonObject.getValue("value").jsonPrimitive.content
                assertEquals(value,apple.replace("%1\$@","%1\$s"),"$locale $key")
            }
        }
    }
}
