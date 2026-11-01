package dev.daymark.release

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class PrivacyMetadataTest {
    private fun root(): Path = if (Files.exists(Path.of("../androidApp"))) Path.of("..") else Path.of(".")
    @Test fun both_languages_are_packaged_for_offline_switching() {
        val build=Files.readString(root().resolve("androidApp/build.gradle.kts"))
        assertTrue(build.contains("enableSplit = false"))
    }
    @Test fun cloud_and_device_transfer_exclude_private_local_state_and_apple_declares_defaults_reason() {
        val extraction=Files.readString(root().resolve("androidApp/src/main/res/xml/data_extraction_rules.xml"))
        for (section in listOf("cloud-backup","device-transfer")) {
            val block=extraction.substringAfter("<$section>").substringBefore("</$section>")
            for (domain in listOf("root","file","database","sharedpref","external"))
                assertTrue(block.contains("domain=\"$domain\" path=\".\""))
        }
        val manifest=Files.readString(root().resolve("androidApp/src/main/AndroidManifest.xml"))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        val apple=Files.readString(root().resolve("iosApp/Daymark/PrivacyInfo.xcprivacy"))
        assertTrue(apple.contains("NSPrivacyAccessedAPICategoryUserDefaults")); assertTrue(apple.contains("CA92.1"))
        assertFalse(apple.contains("<true/>"))
    }
}
