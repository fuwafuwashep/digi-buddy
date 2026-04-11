package com.digibuddy.helper.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary          = Teal600,
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,
    secondary        = Teal400,
    onSecondary      = Color(0xFFFFFFFF),
    secondaryContainer = Teal50,
    onSecondaryContainer = Teal800,
    tertiary         = Amber500,
    background       = Grey50,
    onBackground     = Grey900,
    surface          = Color(0xFFFFFFFF),
    onSurface        = Grey900,
    surfaceVariant   = Grey100,
    onSurfaceVariant = Grey700,
    outline          = Grey300,
    error            = ErrorRed,
    onError          = Color(0xFFFFFFFF),
)

private val DarkColorScheme = darkColorScheme(
    primary          = Teal300,
    onPrimary        = Teal900,
    primaryContainer = Teal800,
    background       = Color(0xFF121212),
    surface          = Color(0xFF1E1E1E),
)

val Typography = Typography(
    titleLarge  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 16.sp),
    bodyMedium  = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 14.sp),
    labelSmall  = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 11.sp),
)

@Composable
fun DigiBuddyHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
