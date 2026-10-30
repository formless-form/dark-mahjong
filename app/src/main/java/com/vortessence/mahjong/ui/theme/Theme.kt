package com.vortessence.mahjong.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark only: there is no light scheme
val MahjongColorScheme = darkColorScheme(
    primary = JadeAccent,
    onPrimary = JadeOnAccent,
    primaryContainer = JadeContainer,
    onPrimaryContainer = JadeOnContainer,
    secondary = AmberAccent,
    onSecondary = AmberOnAccent,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,
    tertiary = MoonAccent,
    onTertiary = MoonOnAccent,
    background = InkBackground,
    onBackground = MistText,
    surface = InkSurface,
    onSurface = MistText,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = MistTextMuted,
    surfaceTint = JadeAccent,
    surfaceDim = InkBackground,
    surfaceBright = InkSurfaceHighest,
    surfaceContainerLowest = InkBackground,
    surfaceContainerLow = InkSurface,
    surfaceContainer = InkSurfaceRaised,
    surfaceContainerHigh = InkSurfaceHigh,
    surfaceContainerHighest = InkSurfaceHighest,
    inverseSurface = MistText,
    inverseOnSurface = InkSurface,
    inversePrimary = JadeContainer,
    outline = InkOutline,
    outlineVariant = InkOutlineSoft,
    error = EmberError,
    onError = Color(0xFF2B0D0A),
    errorContainer = EmberErrorContainer,
    onErrorContainer = EmberOnErrorContainer,
    scrim = Color.Black,
)

@Composable
fun MahjongTheme(
    content: @Composable () -> Unit,
) {
    // Android 17's Material 3 Expressive: springy motion, its larger rounded shapes and the
    // Google Sans Flex type scale, on Dark Mahjong's own dark palette (no wallpaper colours)
    MaterialExpressiveTheme(
        colorScheme = MahjongColorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        typography = Typography,
        content = content,
    )
}
