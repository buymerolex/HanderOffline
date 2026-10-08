package org.hander.novelreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HanderColorScheme = darkColorScheme(
    primary = HanderGold,
    onPrimary = HanderOnGold,
    primaryContainer = HanderTealDeep,
    onPrimaryContainer = HanderIvory,
    secondary = HanderTeal,
    onSecondary = HanderBackground,
    secondaryContainer = HanderTealDeep,
    onSecondaryContainer = HanderIvory,
    tertiary = HanderTealDim,
    background = HanderBackground,
    onBackground = HanderIvory,
    surface = HanderPanel,
    onSurface = HanderIvory,
    surfaceVariant = HanderPanelRaised,
    onSurfaceVariant = HanderMuted,
    outline = HanderBorder,
    outlineVariant = HanderBorder,
)

/** Hander is dark-only by design. */
@Composable
fun HanderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HanderColorScheme,
        typography = HanderTypography,
        content = content,
    )
}
