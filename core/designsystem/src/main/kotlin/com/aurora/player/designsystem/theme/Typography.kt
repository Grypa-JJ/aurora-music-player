package com.aurora.player.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aurora.player.designsystem.R

/**
 * Inter jako pojedynczy plik variable font (`InterVariable.ttf`, oficjalne wydanie rsms/inter
 * v4.1 — od tej wersji projekt dystrybuuje tylko wariant zmiennoprzecinkowy, nie osobne pliki
 * na wagę) — trzy wagi wyprowadzone z jednego pliku przez `FontVariation.Settings`.
 * Patrz DESIGN.md sekcja 2.2. Nigdy pełny Bold (700+) — maks. 3 wagi w całej apce.
 */
@OptIn(ExperimentalTextApi::class)
val AuroraFontFamily = FontFamily(
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight.W400,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight.W500,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight.W600,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

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
