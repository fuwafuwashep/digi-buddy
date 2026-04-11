package com.digibuddy.customer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
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
    onTertiary       = Color(0xFF000000),
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
    onPrimaryContainer = Teal50,
    secondary        = Teal200,
    onSecondary      = Teal900,
    secondaryContainer = Teal700,
    onSecondaryContainer = Teal50,
    tertiary         = Amber400,
    onTertiary       = Color(0xFF000000),
    background       = Color(0xFF121212),
    onBackground     = Color(0xFFEEEEEE),
    surface          = Color(0xFF1E1E1E),
    onSurface        = Color(0xFFEEEEEE),
    surfaceVariant   = Color(0xFF2C2C2C),
    onSurfaceVariant = Grey300,
    outline          = Grey700,
    error            = Color(0xFFCF6679),
    onError          = Color(0xFF000000),
)

@Composable
fun DigiBuddyTheme(
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
