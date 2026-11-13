package com.example.ui.theme

import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.Locale

private val VyoraDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E1E5C),
    onPrimaryContainer = ElectricVioletLight,
    secondary = NeonCyan,
    onSecondary = Color(0xFF04262E),
    secondaryContainer = Color(0xFF0A3240),
    onSecondaryContainer = NeonCyanLight,
    tertiary = PulseMagenta,
    onTertiary = Color.White,
    background = VyoraObsidian,
    onBackground = PureMist,
    surface = VyoraMidnight,
    onSurface = PureMist,
    surfaceVariant = VyoraElevated,
    onSurfaceVariant = MutedLavender,
    outline = VyoraBorderDark,
    error = CrimsonError,
    onError = Color.White
)

private val VyoraLightColorScheme = lightColorScheme(
    primary = RoyalViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = DeepCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = PulseMagenta,
    onTertiary = Color.White,
    background = AlabasterBg,
    onBackground = CharcoalInk,
    surface = PureWhiteSurface,
    onSurface = CharcoalInk,
    surfaceVariant = SoftSlateSurface,
    onSurfaceVariant = SlateSecondary,
    outline = Color(0xFFCBD5E1),
    error = CrimsonError,
    onError = Color.White
)

val VyoraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

val LocalVyoraResources = compositionLocalOf<Resources> {
    error("LocalVyoraResources not provided")
}

@Composable
fun vString(@StringRes id: Int): String {
    return LocalVyoraResources.current.getString(id)
}

@Composable
fun vString(@StringRes id: Int, vararg args: Any): String {
    return LocalVyoraResources.current.getString(id, *args)
}

@Composable
fun VyoraTheme(
    themeMode: String = "dark",
    language: String = "ar",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    val colorScheme = if (isDark) VyoraDarkColorScheme else VyoraLightColorScheme
    val baseContext = LocalContext.current
    val localizedResources = remember(language, baseContext) {
        val locale = Locale.forLanguageTag(language)
        val config = Configuration(baseContext.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        baseContext.createConfigurationContext(config).resources
    }
    val layoutDirection = if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalVyoraResources provides localizedResources
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VyoraTypography,
            shapes = VyoraShapes,
            content = content
        )
    }
}
