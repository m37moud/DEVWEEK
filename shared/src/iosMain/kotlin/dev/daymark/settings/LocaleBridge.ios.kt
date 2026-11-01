package dev.daymark.settings

import androidx.compose.runtime.*
import androidx.compose.ui.uikit.LocalUIViewController
import platform.Foundation.*
import platform.UIKit.*

private object IosAppLocale {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val arguments = defaults.volatileDomainForName(NSArgumentDomain)
    val systemTags = NSLocale.preferredLanguages.mapNotNull { it as? String }
    fun apply(language: AppLanguage) {
        // Argument-domain overrides live only in this process. They do not persist a fake system preference after restart.
        defaults.setVolatileDomain(arguments + mapOf("AppleLanguages" to listOf(language.tag)), forName = NSArgumentDomain)
    }
}
actual fun applyAppLocale(language: AppLanguage) { IosAppLocale.apply(language) }
@Composable actual fun systemLanguageTags(): List<String> = IosAppLocale.systemTags
@Composable actual fun WithPlatformLocale(language: AppLanguage, content: @Composable () -> Unit) { content() }
@Composable actual fun ApplyNativeTheme(preference: ThemePreference) {
    val controller = LocalUIViewController.current
    SideEffect {
        val style = when(preference) {
        ThemePreference.SYSTEM -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        ThemePreference.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
        ThemePreference.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
        controller.overrideUserInterfaceStyle = style
        controller.view.window?.overrideUserInterfaceStyle = style
    }
}
