package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val ClassicMenchColorScheme = darkColorScheme(
    primary = BrassGold,
    onPrimary = WalnutDeep,
    primaryContainer = BrassGoldDark,
    onPrimaryContainer = CreamText,
    secondary = ParchmentAged,
    onSecondary = WalnutDeep,
    secondaryContainer = WalnutSurfaceHigh,
    onSecondaryContainer = CreamText,
    tertiary = VoiceActiveGreen,
    onTertiary = WalnutDeep,
    background = WalnutDeep,
    onBackground = CreamText,
    surface = WalnutSurface,
    onSurface = CreamText,
    surfaceVariant = WalnutSurfaceHigh,
    onSurfaceVariant = MutedSandText,
    outline = WalnutCardBorder,
    error = ErrorCrimson,
    onError = WalnutDeep
)

private val MenchShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MenchClassicTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = ClassicMenchColorScheme,
            typography = Typography,
            shapes = MenchShapes,
            content = content
        )
    }
}
