plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "dev.daymark.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.daymark.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    implementation(project(":shared"))
    implementation(libs.activity.compose)
    implementation(libs.coroutines.core)
    implementation(libs.play.auth)
    implementation(libs.credentials)
    implementation(libs.credentials.google)
    debugImplementation(libs.compose.material3)
    implementation(libs.work.runtime)
    implementation(libs.room.runtime)
    implementation(libs.datetime)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
}
