package com.aurora.player.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// TODO(etap 1): zastąpić prawdziwymi plikami fontu Inter w res/font/ (inter_regular/medium/semibold.ttf)
// i podpiąć przez FontFamily(Font(R.font.inter_regular, FontWeight.W400), ...) — patrz DESIGN.md 2.2.
// Do czasu dodania plików fontu używamy FontFamily.SansSerif jako placeholder z tą samą skalą/wagami.
val AuroraFontFamily = FontFamily.SansSerif

object AuroraTextStyles {
    val Display = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 34.sp * 1.3f,
    )
    val Headline = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 26.sp,
        letterSpacing = (-0.3).sp,
        lineHeight = 26.sp * 1.3f,
    )
    val Title = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 19.sp,
        letterSpacing = (-0.2).sp,
    )
    val Body = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 16.sp,
    )
    val Label = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp,
    )
    val Caption = TextStyle(
        fontFamily = AuroraFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 11.sp,
    )
    /** Czas odtwarzania (00:00) — tabularne cyfry, żeby licznik nie "skakał". */
    val TimeTabular = Caption.copy(
        fontFeatureSettings = "tnum",
    )
}

val AuroraTypography = Typography(
    displayLarge = AuroraTextStyles.Display,
    headlineMedium = AuroraTextStyles.Headline,
    titleMedium = AuroraTextStyles.Title,
    bodyLarge = AuroraTextStyles.Body,
    labelMedium = AuroraTextStyles.Label,
    labelSmall = AuroraTextStyles.Caption,
)
