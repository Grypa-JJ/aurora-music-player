package com.aurora.player.projectm

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Poświata za ramką wizualizera/okładki (Etap 10 część 2, DESIGN.md) — pulsuje energią basu i
 * ogólną głośnością (te same sygnały co stary `AuroraVisualizer`, liczone niezależnie od tego czy
 * aktywny jest projectM czy fallback), podbarwiona kolorem wg [GlowColorSource].
 *
 * ŚWIADOMIE nie próbuje "przefarbować" samego renderu projectM (presety same rządzą swoimi
 * kolorami przez shadery, patrz DESIGN.md) — to tylko warstwa WOKÓŁ, umieszczona w drzewie Compose
 * pod właściwą ramką (rysowana pierwsza = niżej), nie nad nią.
 *
 * `:core:projectm` świadomie NIE zależy od `:app` — parametry to proste `Float`/`Color`, nie
 * `VisualizerFrame`/`AlbumArtPalette` z app-specyficznych typów. Wołający (np. `NowPlayingScreen`)
 * odpowiada za przekazanie prawdziwych wartości.
 */
@Composable
fun AmbientGlow(
    bassEnergy: Float,
    overallEnergy: Float,
    color: Color,
    colorSource: GlowColorSource,
    modifier: Modifier = Modifier,
) {
    val effectiveColor = when (colorSource) {
        GlowColorSource.ALBUM_ART -> color
        GlowColorSource.MONOCHROME -> Color.White
    }

    val intensity by animateFloatAsState(
        targetValue = (0.25f + 0.75f * overallEnergy.coerceIn(0f, 1f)),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ambientGlowIntensity",
    )
    val scale by animateFloatAsState(
        targetValue = 1f + 0.18f * bassEnergy.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ambientGlowScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        effectiveColor.copy(alpha = 0.5f * intensity),
                        effectiveColor.copy(alpha = 0.15f * intensity),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}
