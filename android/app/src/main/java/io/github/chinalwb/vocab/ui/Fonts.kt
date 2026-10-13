package io.github.chinalwb.vocab.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import io.github.chinalwb.vocab.R

/*
 * 字体 (用户 2026-10-13): one family for the whole app, Atkinson Hyperlegible ("tidy and neat",
 * chosen from a side-by-side of ten). It comes from Google's downloadable-fonts provider (Play
 * services), so the APK doesn't grow; until it's fetched, or offline on first launch, text shows
 * in the system font. Chinese has no glyphs in it and stays the system's. To switch fonts — or to
 * build a font setting later — change [APP_FONT_NAME] / [AppFont] only.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

const val APP_FONT_NAME = "Atkinson Hyperlegible"

/** Atkinson comes in 400 and 700 (and italics); Medium / SemiBold requests match the nearest. */
val AppFont: FontFamily = GoogleFont(APP_FONT_NAME).let { f ->
    FontFamily(
        Font(f, provider, FontWeight.Normal),
        Font(f, provider, FontWeight.Bold),
        Font(f, provider, FontWeight.Normal, FontStyle.Italic),
        Font(f, provider, FontWeight.Bold, FontStyle.Italic),
    )
}

/**
 * Every text a little bigger (用户 2026-10-13): applied on top of the phone's own font-size
 * setting, so it scales all sp at once — Material styles and the hard-coded sizes alike.
 */
const val FONT_SCALE = 1.08f

private fun TextStyle.app() = copy(fontFamily = AppFont)

val AppTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.app(), displayMedium = displayMedium.app(), displaySmall = displaySmall.app(),
        headlineLarge = headlineLarge.app(), headlineMedium = headlineMedium.app(), headlineSmall = headlineSmall.app(),
        titleLarge = titleLarge.app(), titleMedium = titleMedium.app(), titleSmall = titleSmall.app(),
        bodyLarge = bodyLarge.app(), bodyMedium = bodyMedium.app(), bodySmall = bodySmall.app(),
        labelLarge = labelLarge.app(), labelMedium = labelMedium.app(), labelSmall = labelSmall.app(),
    )
}
