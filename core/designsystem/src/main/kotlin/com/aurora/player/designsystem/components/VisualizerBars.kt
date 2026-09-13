package com.aurora.player.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Wizualizer widmowy à la stary Windows Media Player — słupki energii per pasmo częstotliwości.
 * [magnitudes] to znormalizowane wartości 0..1 z `AudioVisualizerAnalyzer` (app module); ten
 * komponent nie zna nic o silniku DSP, tylko rysuje to, co dostał (patrz DESIGN.md, wzorzec
 * "core:designsystem niezależny od reszty appki" — jak w TrackListItem/MiniPlayerBar).
 */
@Composable
fun VisualizerBars(
    magnitudes: FloatArray,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animatedHeights = magnitudes.map { target ->
        animateFloatAsState(
            targetValue = target.coerceIn(0f, 1f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "visualizerBar",
        ).value
    }

    Canvas(modifier = modifier) {
        val barCount = animatedHeights.size
        if (barCount == 0) return@Canvas

        val gap = size.width * 0.012f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount

        animatedHeights.forEachIndexed { index, heightFraction ->
            val barHeight = (size.height * heightFraction).coerceAtLeast(barWidth * 0.6f)
            drawRoundRect(
                color = color,
                topLeft = Offset(x = index * (barWidth + gap), y = size.height - barHeight),
                size = Size(width = barWidth, height = barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
