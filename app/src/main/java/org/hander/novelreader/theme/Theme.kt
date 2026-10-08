package org.hander.novelreader.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fixed brand palette - Hander is always dark, matching the logo.
object HanderColors {
    val Background = Color(0xFF080D0F)   // Obsidian Black
    val Panel = Color(0xFF11191C)        // Charcoal
    val Border = Color(0xFF293237)       // Slate Gray
    val Background2 = Color(0xFF0B1A20)  // Deep Blue-Black
    val HighlightBg = Color(0xFF16343A)  // Dark Cyan
    val Accent = Color(0xFF3F7277)       // teal accent / icons / progress
    val Accent2 = Color(0xFF6D9797)      // secondary accent
    val Gold = Color(0xFFC9B98A)         // important accents
    val Text = Color(0xFFE5DDC9)         // warm ivory
}

private val HanderScheme = darkColorScheme(
    primary = HanderColors.Accent,
    onPrimary = HanderColors.Background,
    secondary = HanderColors.Accent2,
    onSecondary = HanderColors.Background,
    tertiary = HanderColors.Gold,
    background = HanderColors.Background,
    onBackground = HanderColors.Text,
    surface = HanderColors.Panel,
    onSurface = HanderColors.Text,
    surfaceVariant = HanderColors.Background2,
    outline = HanderColors.Border,
    error = Color(0xFFCF6679),
)

@Composable
fun HanderTheme(content: @Composable () -> Unit) {
    // Intentionally ignoring system light/dark setting - Hander is always dark.
    MaterialTheme(colorScheme = HanderScheme, content = content)
}
