package dev.daymark.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import dev.daymark.settings.*

private val Light = lightColorScheme(primary = Color(0xFF28664D), onPrimary = Color(0xFFF6FAEF),
    primaryContainer = Color(0xFFE1EFCF), onPrimaryContainer = Color(0xFF20392D),
    secondary = Color(0xFF526352), onSecondary = Color.White, secondaryContainer = Color(0xFFDAE8D2), onSecondaryContainer = Color(0xFF1A2B1D),
    tertiary = Color(0xFF94651C), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFF1D4), onTertiaryContainer = Color(0xFF433621),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FAF5), surfaceContainer = Color(0xFFEFF4EB),
    surfaceContainerHigh = Color(0xFFE8F0E3), surfaceContainerHighest = Color(0xFFE1EADD),
    background = Color(0xFFF8FAF5), onBackground = Color(0xFF20392D), surface = Color.White, onSurface = Color(0xFF20392D),
    surfaceVariant = Color(0xFFEFF4EB), onSurfaceVariant = Color(0xFF56695C), outline = Color(0xFF7A897C),
    outlineVariant = Color(0xFFE4EADE), error = Color(0xFFAB5249), errorContainer = Color(0xFFFBE7E0), onErrorContainer = Color(0xFF72322C))
private val Dark = darkColorScheme(primary = Color(0xFFC2DF9D), onPrimary = Color(0xFF21351E),
    primaryContainer = Color(0xFF35482C), onPrimaryContainer = Color(0xFFEDF2E7),
    secondary = Color(0xFFB8CCAC), onSecondary = Color(0xFF253423), secondaryContainer = Color(0xFF35482C), onSecondaryContainer = Color(0xFFDCE8D0),
    tertiary = Color(0xFFF1C986), onTertiary = Color(0xFF433621), tertiaryContainer = Color(0xFF433621), onTertiaryContainer = Color(0xFFF1C986),
    surfaceContainerLowest = Color(0xFF0D1711), surfaceContainerLow = Color(0xFF17251C), surfaceContainer = Color(0xFF1D2C24),
    surfaceContainerHigh = Color(0xFF25372C), surfaceContainerHighest = Color(0xFF2D4034),
    background = Color(0xFF121D18), onBackground = Color(0xFFEDF2E7), surface = Color(0xFF1D2C24), onSurface = Color(0xFFEDF2E7),
    surfaceVariant = Color(0xFF25372C), onSurfaceVariant = Color(0xFFA1B09E), outline = Color(0xFF81927F),
    outlineVariant = Color(0xFF304134), error = Color(0xFFF2ADA1), errorContainer = Color(0xFF452E29), onErrorContainer = Color(0xFFF2ADA1))
@Composable fun DaymarkTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val dark = when(preference) { ThemePreference.SYSTEM -> isSystemInDarkTheme(); ThemePreference.LIGHT -> false; ThemePreference.DARK -> true }
    ApplyNativeTheme(preference)
    MaterialTheme(colorScheme = if(dark) Dark else Light, shapes = Shapes(small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)), content = content)
}
