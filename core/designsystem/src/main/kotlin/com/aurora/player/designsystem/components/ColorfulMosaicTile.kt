package com.aurora.player.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.theme.AuroraLightTextPrimary
import com.aurora.player.designsystem.theme.AuroraTextPrimary
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens

/**
 * Kafel "mozaiki" kolorów (Etap 37, poprawka po feedbacku usera: porównanie ze zrzutem Spotify
 * Wrapped — "ma być kolorowo, spójnie, czytelnie, bez powtarzania tych samych elementów wszędzie").
 * W przeciwieństwie do `DomainShortcutCard` (ciemne tło karty + mały tinted-krążek z ikoną), tu
 * CAŁA karta jest wypełniona pełnym, nasyconym kolorem (`backgroundColor`) — a tekst/ikona dobierają
 * kontrast automatycznie na podstawie luminancji tego koloru, żeby nie zgadywać na sztywno który
 * kolor potrzebuje ciemnego, a który jasnego tekstu.
 *
 * Reużywany identycznie na Home (8 domen) i Odkrywaj (5 domen zewnętrznych) — ta sama struktura
 * karty w obu miejscach, różni się tylko zawartość siatki, co daje spójność wizualną, o którą
 * prosił user.
 */
@Composable
fun ColorfulMosaicTile(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val tokens = LocalAuroraTokens.current
    val contentColor = contentColorFor(backgroundColor)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(tokens.spacing.m),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(32.dp),
        )
        Column {
            Text(
                text = label,
                style = AuroraTextStyles.Title,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AuroraTextStyles.Caption,
                    color = contentColor.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Prosty dobór kontrastu tekst/ikona na podstawie percepcyjnej luminancji tła (wagi ITU-R BT.601 —
 * wystarczające dla heurystyki UI, nie do zastosowań kolorymetrycznych). Próg 0.55 dobrany tak, by
 * jaskrawy bursztyn/żółty (Podkasty) dostawał ciemny tekst, a granat/fiolet/magenta — jasny.
 */
private fun contentColorFor(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.55f) AuroraLightTextPrimary else AuroraTextPrimary
}
