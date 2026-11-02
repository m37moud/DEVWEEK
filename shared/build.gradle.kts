import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

class CrashProbeClasspath(@get:Classpath val files: FileCollection) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-Ddaymark.test.classpath=${files.asPath}")
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}
kotlin {
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
    android {
        namespace = "dev.daymark.shared"
        androidResources.enable = true
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
            implementation(libs.material.icons.core)
            implementation(libs.compose.resources)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.navigation3)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.datetime)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.ktor.core)
            implementation(libs.font.awesome)

        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.mock)
        }
        androidMain.dependencies {
            implementation(libs.play.location); implementation(libs.ktor.android); implementation(
            libs.activity.compose
        )
        }
        iosMain.dependencies { implementation(libs.ktor.darwin) }
        jvmMain {
            kotlin.srcDir("src/androidMain/kotlin/dev/daymark/plan")
            kotlin.srcDir("src/androidMain/kotlin/dev/daymark/settings/format")
        }
        jvmTest.dependencies { implementation(libs.kotlin.test.junit); implementation(libs.junit) }
        getByName("androidHostTest").dependencies { implementation(libs.junit) }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.test.runner); implementation(
            libs.test.ext.junit
        )
        }
    }
}
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
room3 { schemaDirectory("$projectDir/schemas") }
tasks.named<Test>("jvmTest") { jvmArgumentProviders.add(CrashProbeClasspath(classpath)) }
compose.resources { publicResClass = true; packageOfResClass = "dev.daymark.resources" }
