package com.digibuddy.shared.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object DigibuddyColors {
    val Teal = Color(0xFF007F86)
    val BrightTeal = Color(0xFF00B8C2)
    val DeepTeal = Color(0xFF004F54)
    val OffWhite = Color(0xFFF6FAFA)
    val Mist = Color(0xFFE7F3F3)
    val Navy = Color(0xFF17324D)
    val Coral = Color(0xFFFF8066)
    val Gold = Color(0xFFF5B942)
    val Success = Color(0xFF2E7D5B)
    val Slate = Color(0xFF536471)
}

private val LightColors =
    lightColorScheme(
        primary = DigibuddyColors.Teal,
        secondary = DigibuddyColors.BrightTeal,
        background = DigibuddyColors.OffWhite,
        surface = Color.White,
        surfaceVariant = DigibuddyColors.Mist,
        onPrimary = Color.White,
        onBackground = DigibuddyColors.DeepTeal,
        onSurface = DigibuddyColors.DeepTeal,
        onSurfaceVariant = DigibuddyColors.Slate,
        tertiary = DigibuddyColors.Coral,
        outline = Color(0xFFB8CCCC),
    )

private val DigibuddyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val DarkColors =
    darkColorScheme(
        primary = DigibuddyColors.BrightTeal,
        secondary = DigibuddyColors.Teal,
        background = Color(0xFF071C1E),
        surface = Color(0xFF0C272A),
        onPrimary = Color(0xFF002022),
        onBackground = Color.White,
        onSurface = Color.White,
    )

@Composable
fun DigibuddyTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = DigibuddyShapes,
        content = content,
    )
}
