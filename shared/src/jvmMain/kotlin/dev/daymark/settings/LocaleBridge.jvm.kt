package dev.daymark.settings

import androidx.compose.runtime.Composable
import java.util.Locale

private val systemTags = listOf(Locale.getDefault().toLanguageTag())
actual fun applyAppLocale(language: AppLanguage) { Locale.setDefault(Locale.forLanguageTag(language.tag)) }
@Composable actual fun systemLanguageTags() = systemTags
@Composable actual fun WithPlatformLocale(language: AppLanguage, content: @Composable () -> Unit) { content() }
@Composable actual fun ApplyNativeTheme(preference: ThemePreference) = Unit
