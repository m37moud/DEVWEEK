package dev.daymark.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.util.Locale

actual fun applyAppLocale(language: AppLanguage) { Locale.setDefault(Locale.forLanguageTag(language.tag)) }
@Composable actual fun systemLanguageTags(): List<String> {
    // Read the host configuration before our scoped override, including any OS per-app language preference.
    val locales = LocalConfiguration.current.locales
    return (0 until locales.size()).map { locales[it].toLanguageTag() }
}
@Composable actual fun WithPlatformLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = LocalConfiguration.current
    val configuration = remember(base, language) { Configuration(base).apply { setLocale(Locale.forLanguageTag(language.tag)) } }
    val localized = remember(context, configuration) { context.createConfigurationContext(configuration) }
    CompositionLocalProvider(LocalConfiguration provides configuration, LocalContext provides localized, content = content)
}
@Composable actual fun ApplyNativeTheme(preference: ThemePreference) {
    val dark = when(preference) { ThemePreference.DARK -> true; ThemePreference.LIGHT -> false; ThemePreference.SYSTEM -> isSystemInDarkTheme() }
    val view = LocalView.current
    SideEffect {
        view.context.activity()?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x801B1B1B.toInt()) { dark })
    }
}
private fun Context.activity(): ComponentActivity? {
    var current: Context = this
    while (current is ContextWrapper) { if (current is ComponentActivity) return current; current = current.baseContext }
    return current as? ComponentActivity
}
