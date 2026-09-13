package com.aurora.player.designsystem.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Wizualizer generatywny à la Winamp/WMP z lat 2000 (MilkDrop/AVS/Ambience) — nie płaski pasek
 * słupków, tylko trzy nałożone na siebie warstwy rysowane w jednym Canvasie, klatka po klatce:
 *
 * 1. Pulsujący blask w tle (radialny gradient, rozmiar/intensywność zależne od głośności+basu).
 * 2. Radialne widmo — 24 "promienie" świecące (natywny `BlurMaskFilter` pod spodem + ostra
 *    kreska na wierzchu) rozstawione w okrąg, długość = energia danego pasma częstotliwości.
 * 3. System cząsteczek — eksplodują z centrum przy każdym wykrytym uderzeniu basu
 *    ([beatCount] rośnie w [com.aurora.player.visualizer.AudioVisualizerAnalyzer]), fizyka
 *    (prędkość + tarcie) liczona w pętli klatek, nie w Compose recomposition.
 *
 * Świadomie jeden akcent koloru (zgodnie z DESIGN.md "jeden mocny akcent na ekran") zamiast
 * tęczy — kolor ten sam co reszta Now Playing (Vibrant z okładki), więc wizualizer nie wygląda
 * jak osobny, niespójny element.
 *
 * Pętla klatek jedzie przez `withFrameNanos` (zegar odświeżania ekranu, nie sztywne 60fps) i
 * wymusza przerysowanie Canvasu przez jawny odczyt `frameTick` w fazie rysowania — dane wejściowe
 * (`bandMagnitudes`/`bassEnergy`/...) czytane przez `rememberUpdatedState`, żeby długo działająca
 * pętla `LaunchedEffect(Unit)` zawsze widziała najnowsze wartości bez restartowania coroutine.
 */
@Composable
fun AuroraVisualizer(
    bandMagnitudes: FloatArray,
    bassEnergy: Float,
    overallEnergy: Float,
    beatCount: Long,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val particles = remember { mutableStateListOf<AuroraParticle>() }
    var frameTick by remember { mutableLongStateOf(0L) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    var lastBeatCount by remember { mutableLongStateOf(beatCount) }

    val bassEnergyState = rememberUpdatedState(bassEnergy)
    val beatCountState = rememberUpdatedState(beatCount)
    val accentColorState = rememberUpdatedState(accentColor)

    val glowPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nowNanos ->
                val dtSec = if (lastFrameNanos == 0L) {
                    0.016f
                } else {
                    ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                }
                lastFrameNanos = nowNanos

                if (beatCountState.value != lastBeatCount) {
                    lastBeatCount = beatCountState.value
                    spawnBurst(particles, accentColorState.value, bassEnergyState.value)
                }

                val iterator = particles.listIterator()
                while (iterator.hasNext()) {
                    val particle = iterator.next()
                    particle.ageMs += dtSec * 1000f
                    if (particle.ageMs >= particle.lifeMs) {
                        iterator.remove()
                        continue
                    }
                    particle.x += particle.vx * dtSec
                    particle.y += particle.vy * dtSec
                    particle.vx *= PARTICLE_DRAG
                    particle.vy *= PARTICLE_DRAG
                }

                frameTick = nowNanos
            }
        }
    }

    Canvas(modifier = modifier) {
        // Jawny odczyt w fazie rysowania — Compose wie, że ma przerysować tę klatkę.
        frameTick

        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f
        val innerRadius = maxRadius * 0.42f

        drawPulseGlow(center, maxRadius, overallEnergy, bassEnergy, accentColor)
        drawRadialSpectrum(center, innerRadius, maxRadius, bandMagnitudes, accentColor, glowPaint)
        drawAuroraParticles(particles, center)
    }
}

private class AuroraParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var ageMs: Float,
    val lifeMs: Float,
    val color: Color,
    val startRadius: Float,
)

private fun spawnBurst(particles: MutableList<AuroraParticle>, color: Color, bassEnergy: Float) {
    repeat(PARTICLES_PER_BEAT) {
        val angle = Random.nextFloat() * (2f * PI_F)
        val speed = PARTICLE_MIN_SPEED + Random.nextFloat() * PARTICLE_SPEED_RANGE * (0.6f + bassEnergy)
        particles.add(
            AuroraParticle(
                x = 0f,
                y = 0f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                ageMs = 0f,
                lifeMs = PARTICLE_MIN_LIFE_MS + Random.nextFloat() * PARTICLE_LIFE_RANGE_MS,
                color = color,
                startRadius = PARTICLE_MIN_RADIUS + Random.nextFloat() * PARTICLE_RADIUS_RANGE,
            ),
        )
    }
    while (particles.size > MAX_PARTICLES) {
        particles.removeAt(0)
    }
}

private fun DrawScope.drawPulseGlow(
    center: Offset,
    maxRadius: Float,
    overallEnergy: Float,
    bassEnergy: Float,
    color: Color,
) {
    val pulse = (overallEnergy * 0.6f + bassEnergy * 0.4f).coerceIn(0f, 1f)
    val radius = maxRadius * (0.55f + pulse * 0.5f)
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.05f + 0.35f * pulse), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawRadialSpectrum(
    center: Offset,
    innerRadius: Float,
    maxRadius: Float,
    bandMagnitudes: FloatArray,
    color: Color,
    glowPaint: android.graphics.Paint,
) {
    val bandCount = bandMagnitudes.size
    if (bandCount == 0 || innerRadius <= 0f) return

    val strokeWidthPx = (2f * PI_F * innerRadius / bandCount) * 0.55f
    val glowRadiusPx = strokeWidthPx * 2.2f
    glowPaint.strokeWidth = strokeWidthPx
    // BlurMaskFilter zależy od strokeWidthPx (stała per klatka, nie per pasmo) — jedna alokacja
    // na klatkę zamiast 24, reszta (kolor) mutowana na tym samym obiekcie Paint w pętli niżej.
    glowPaint.maskFilter = BlurMaskFilter(glowRadiusPx, BlurMaskFilter.Blur.NORMAL)

    for (i in 0 until bandCount) {
        val angle = (i / bandCount.toFloat()) * 2f * PI_F - (PI_F / 2f)
        val magnitude = bandMagnitudes[i].coerceIn(0f, 1f)
        val barLength = (maxRadius - innerRadius) * magnitude
        val startX = center.x + cos(angle) * innerRadius
        val startY = center.y + sin(angle) * innerRadius
        val endX = center.x + cos(angle) * (innerRadius + barLength)
        val endY = center.y + sin(angle) * (innerRadius + barLength)

        // Poświata pod spodem — degraduje się bezpiecznie, jeśli canvas nie wspiera
        // BlurMaskFilter (po prostu niewidoczna, appka się nie wywala).
        glowPaint.color = color.copy(alpha = 0.55f * magnitude).toArgb()
        drawContext.canvas.nativeCanvas.drawLine(startX, startY, endX, endY, glowPaint)

        drawLine(
            color = color.copy(alpha = 0.7f + magnitude * 0.3f),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawAuroraParticles(particles: List<AuroraParticle>, center: Offset) {
    for (particle in particles) {
        val lifeRatio = (particle.ageMs / particle.lifeMs).coerceIn(0f, 1f)
        val alpha = 1f - lifeRatio
        val radius = particle.startRadius * (1f - lifeRatio * 0.6f)
        if (radius <= 0f || alpha <= 0f) continue
        drawCircle(
            color = particle.color.copy(alpha = alpha),
            radius = radius,
            center = Offset(center.x + particle.x, center.y + particle.y),
        )
    }
}

private val PI_F = PI.toFloat()
private const val MAX_PARTICLES = 160
private const val PARTICLES_PER_BEAT = 18
private const val PARTICLE_MIN_SPEED = 150f
private const val PARTICLE_SPEED_RANGE = 450f
private const val PARTICLE_MIN_LIFE_MS = 500f
private const val PARTICLE_LIFE_RANGE_MS = 500f
private const val PARTICLE_MIN_RADIUS = 3f
private const val PARTICLE_RADIUS_RANGE = 6f
private const val PARTICLE_DRAG = 0.96f
