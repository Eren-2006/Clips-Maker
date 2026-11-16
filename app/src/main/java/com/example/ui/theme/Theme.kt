package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ClipMasterDarkColorScheme = darkColorScheme(
    primary = KineticYellow,
    onPrimary = OnKineticYellow,
    primaryContainer = KineticYellowContainer,
    onPrimaryContainer = KineticYellow,
    secondary = ElectricCyan,
    onSecondary = StudioCanvas,
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = OnElectricCyanContainer,
    tertiary = EmeraldSuccess,
    onTertiary = StudioCanvas,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldSuccess,
    background = StudioCanvas,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioHairline,
    outlineVariant = StudioHairline,
    error = CoralError,
    onError = StudioCanvas,
    errorContainer = CoralErrorContainer,
    onErrorContainer = OnCoralErrorContainer,
)

val ClipMasterShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ClipMasterTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ClipMasterDarkColorScheme,
        typography = Typography,
        shapes = ClipMasterShapes,
        content = content,
    )
}
