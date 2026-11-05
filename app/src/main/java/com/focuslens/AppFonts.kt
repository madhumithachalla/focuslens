package com.focuslens

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The website's typeface (Plus Jakarta Sans), bundled so the app looks the same as the website.
 * Both fonts are under the SIL Open Font License; the files are in res/font.
 */
val AppFontFamily = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

/** The website's monospace face (JetBrains Mono), for numbers and timers. */
val AppMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Material text styles with the website font applied to every one. */
val AppTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = AppFontFamily),
        displayMedium = t.displayMedium.copy(fontFamily = AppFontFamily),
        displaySmall = t.displaySmall.copy(fontFamily = AppFontFamily),
        headlineLarge = t.headlineLarge.copy(fontFamily = AppFontFamily),
        headlineMedium = t.headlineMedium.copy(fontFamily = AppFontFamily),
        headlineSmall = t.headlineSmall.copy(fontFamily = AppFontFamily),
        titleLarge = t.titleLarge.copy(fontFamily = AppFontFamily),
        titleMedium = t.titleMedium.copy(fontFamily = AppFontFamily),
        titleSmall = t.titleSmall.copy(fontFamily = AppFontFamily),
        bodyLarge = t.bodyLarge.copy(fontFamily = AppFontFamily),
        bodyMedium = t.bodyMedium.copy(fontFamily = AppFontFamily),
        bodySmall = t.bodySmall.copy(fontFamily = AppFontFamily),
        labelLarge = t.labelLarge.copy(fontFamily = AppFontFamily),
        labelMedium = t.labelMedium.copy(fontFamily = AppFontFamily),
        labelSmall = t.labelSmall.copy(fontFamily = AppFontFamily),
    )
}
