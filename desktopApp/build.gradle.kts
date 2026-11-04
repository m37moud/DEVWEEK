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

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "dev.daymark.shared.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.daymark.shared"
            packageVersion = "1.0.0"
        }
    }
}
