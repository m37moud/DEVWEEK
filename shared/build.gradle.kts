import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}
kotlin {
    android {
        namespace = "dev.daymark.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest { }
        withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "DaymarkShared"; isStatic = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        getByName("androidHostTest").dependencies { implementation(libs.junit) }
        getByName("androidDeviceTest").dependencies { implementation(libs.test.runner); implementation(libs.test.ext.junit) }
    }
}
compose.resources { publicResClass = true; packageOfResClass = "dev.daymark.resources" }
