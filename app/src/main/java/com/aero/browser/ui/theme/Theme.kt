package com.aero.browser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import com.aero.browser.core.storage.SecurePreferences

private val LocalThemeMode = staticCompositionLocalOf { "System" }
private val LocalDynamicColor = staticCompositionLocalOf { true }

object ThemePreferences {
    var mode by mutableStateOf("System")
    var dynamicColor by mutableStateOf(true)
}

val themeMode: String @Composable get() = LocalThemeMode.current
val usesDynamicColor: Boolean @Composable get() = LocalDynamicColor.current

private val LightColors = lightColorScheme(
    primary = SkyBlue40,
    secondary = SkyBlue60,
    tertiary = DeepBlue40,
)

private val DarkColors = darkColorScheme(
    primary = SkyBlue80,
    secondary = SkyBlue60,
    tertiary = DeepBlue80,
)

private val AeroShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Aero theme. Mirrors the desktop `theme: system|light|dark` setting contract:
 * dynamic color is used on Android 12+, falling back to the Aero sky-blue
 * palette elsewhere.
 */
@Composable
fun AeroTheme(
    darkTheme: Boolean = ThemePreferences.mode.let { mode -> if (mode == "System") isSystemInDarkTheme() else mode == "Dark" },
    dynamicColor: Boolean = ThemePreferences.dynamicColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val securePreferences = remember(context) { SecurePreferences.get(context) }
    LaunchedEffect(securePreferences) {
        ThemePreferences.mode = securePreferences.getString("browser.theme", "System") ?: "System"
        ThemePreferences.dynamicColor = securePreferences.getBoolean("browser.dynamicColor", true)
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalThemeMode provides ThemePreferences.mode, LocalDynamicColor provides dynamicColor) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AeroShapes,
            content = content,
        )
    }
}
