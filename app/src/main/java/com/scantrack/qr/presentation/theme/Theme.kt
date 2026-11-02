package com.scantrack.qr.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary, onPrimary = Color(0xFF102657),
    primaryContainer = Color(0xFF203565), onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFF6ADBB7), onSecondary = Color(0xFF003829),
    secondaryContainer = Color(0xFF104B3C), onSecondaryContainer = Color(0xFFA8F2D6),
    tertiary = Color(0xFFFFCC80), onTertiary = Color(0xFF432B00),
    tertiaryContainer = Color(0xFF594016), onTertiaryContainer = Color(0xFFFFDEAA),
    background = DarkBackground, onBackground = DarkText,
    surface = DarkSurface, onSurface = DarkText,
    surfaceVariant = DarkElevatedSurface, onSurfaceVariant = DarkSecondaryText,
    outline = Color(0xFF7B8A9F), outlineVariant = Color(0xFF303C50),
    error = Color(0xFFFFB3BC), onError = Color(0xFF650019),
    errorContainer = Color(0xFF61182B), onErrorContainer = Color(0xFFFFD9DF),
    inverseSurface = Color(0xFFE6EAF2), inverseOnSurface = LightText,
    inversePrimary = PrimaryBlue, scrim = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EAFF), onPrimaryContainer = Color(0xFF142C6B),
    secondary = ScanTrackColors.Success, onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F4E7), onSecondaryContainer = Color(0xFF064B36),
    tertiary = ScanTrackColors.Warning, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEAC5), onTertiaryContainer = Color(0xFF573900),
    background = LightBackground, onBackground = LightText,
    surface = LightSurface, onSurface = LightText,
    surfaceVariant = LightElevatedSurface, onSurfaceVariant = LightSecondaryText,
    outline = Color(0xFF718097), outlineVariant = Color(0xFFD9E1ED),
    error = ScanTrackColors.Danger, onError = Color.White,
    errorContainer = Color(0xFFFFD9DF), onErrorContainer = Color(0xFF650019),
    inverseSurface = Color(0xFF243044), inverseOnSurface = DarkText,
    inversePrimary = DarkPrimary, scrim = Color.Black
)

@Composable
fun ScanTrackQRTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
