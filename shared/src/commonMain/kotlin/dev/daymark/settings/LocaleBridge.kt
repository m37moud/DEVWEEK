package dev.daymark.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }
expect fun applyAppLocale(language: AppLanguage)
@Composable
expect fun systemLanguageTags(): List<String>
@Composable
expect fun WithPlatformLocale(language: AppLanguage, content: @Composable () -> Unit)
@Composable
expect fun ApplyNativeTheme(preference: ThemePreference)

/** Reset only resource caches when language changes; navigation and form ViewModels keep their state. */
@Composable
fun str(resource: StringResource, vararg args: Any): String =
    key(LocalAppLanguage.current) { stringResource(resource, *args) }

@Composable
fun plural(resource: PluralStringResource, quantity: Int, vararg args: Any): String =
    key(LocalAppLanguage.current) { pluralStringResource(resource, quantity, *args) }
