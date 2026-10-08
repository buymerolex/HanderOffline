package org.hander.novelreader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

private fun TextStyle.serif() = copy(fontFamily = FontFamily.Serif)

private val base = Typography()

val HanderTypography = Typography(
    displayLarge = base.displayLarge.serif(),
    displayMedium = base.displayMedium.serif(),
    displaySmall = base.displaySmall.serif(),
    headlineLarge = base.headlineLarge.serif(),
    headlineMedium = base.headlineMedium.serif(),
    headlineSmall = base.headlineSmall.serif(),
    titleLarge = base.titleLarge.serif(),
    titleMedium = base.titleMedium.serif(),
    titleSmall = base.titleSmall.serif(),
    bodyLarge = base.bodyLarge.serif(),
    bodyMedium = base.bodyMedium.serif(),
    bodySmall = base.bodySmall.serif(),
    labelLarge = base.labelLarge.serif(),
    labelMedium = base.labelMedium.serif(),
    labelSmall = base.labelSmall.serif(),
)
