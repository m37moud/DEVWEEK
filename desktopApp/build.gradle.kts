import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
//
//    alias(libs.plugins.kotlin.multiplatform)
//    alias(libs.plugins.android.kmp)
//    alias(libs.plugins.android.lint)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.room.runtime)
    implementation(libs.ktor.java)
    implementation(libs.serialization.json)
    implementation(libs.compose.resources)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
    implementation(libs.slf4j.nop)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.coroutines.test)

}

val macLocationResources = layout.buildDirectory.dir("generated/locationResources")
val macLocationBundle = macLocationResources.map { it.dir("macos/Daymark Location.app/Contents") }
val prepareMacLocation = tasks.register<Sync>("prepareMacLocation") {
    from("src/main/macos/Info.plist")
    into(macLocationBundle)
    into("Resources") { from("src/main/macos") { exclude("Info.plist") } }
    onlyIf { System.getProperty("os.name").startsWith("Mac") }
}
val compileMacLocation = tasks.register<Exec>("compileMacLocation") {
    dependsOn(prepareMacLocation)
    inputs.file("src/main/swift/DaymarkLocation.swift")
    val executable = macLocationBundle.map { it.file("MacOS/DaymarkLocation") }
    outputs.file(executable)
    onlyIf { System.getProperty("os.name").startsWith("Mac") }
    doFirst { executable.get().asFile.parentFile.mkdirs() }
    commandLine(
        "xcrun", "swiftc", "-O", "src/main/swift/DaymarkLocation.swift",
        "-o", executable.get().asFile.absolutePath
    )
}

val compileMacGoogleSession = tasks.register<Exec>("compileMacGoogleSession") {
    dependsOn(prepareMacLocation)
    inputs.file("src/main/swift/DaymarkGoogleSession.swift")
    val executable = macLocationBundle.map { it.file("MacOS/DaymarkGoogleSession") }
    outputs.file(executable)
    onlyIf { System.getProperty("os.name").startsWith("Mac") }
    doFirst { executable.get().asFile.parentFile.mkdirs() }
    commandLine("xcrun", "swiftc", "-O", "src/main/swift/DaymarkGoogleSession.swift",
        "-o", executable.get().asFile.absolutePath)
}

sourceSets.main {
    resources.srcDir(macLocationResources.map { it.dir("macos") })
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(compileMacLocation, compileMacGoogleSession)
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(compileMacLocation, compileMacGoogleSession)
}

compose.desktop {
    application {
        mainClass = "dev.daymark.shared.MainKt"

        nativeDistributions {
            modules("jdk.httpserver", "java.net.http")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.daymark.shared"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(macLocationResources)
        }
    }
}
