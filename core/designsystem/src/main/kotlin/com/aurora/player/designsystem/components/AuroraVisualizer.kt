package com.aurora.player.designsystem.components

import android.graphics.BlurMaskFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Wizualizer generatywny à la Winamp MilkDrop/AVS / WMP Ambience — **pełnoekranowy**, nie mały
 * pasek. Na życzenie użytkownika ("1000 poziomów wyżej") świadomie łamie zasadę DESIGN.md
 * "jeden akcent koloru na ekran" — to jedno konkretne miejsce w appce ma być czystym spektaklem,
 * kolor cyklicznie płynie przez całe spektrum barw (HSV), nie trzyma się marki.
 *
 * Kluczowa różnica względem pierwszej wersji: rysowanie NIE dzieje się bezpośrednio w Compose
 * `Canvas` (który czyści się do zera co klatkę) tylko na **persystentnej offscreen bitmapie**
 * (`ImageBitmap` + natywny `android.graphics.Canvas`), na którą każda klatka najpierw nakłada
 * półprzezroczysty czarny prostokąt (przypalenie/fade) zamiast pełnego czyszczenia — to daje
 * prawdziwe płynące smugi/motion-blur, nie da się tego osiągnąć zwykłym Compose Canvasem.
 * Compose `Canvas` w tym pliku tylko "blituje" gotową bitmapę na ekran.
 *
 * Trzy warstwy na klatkę: (1) pulsujący radialny blask, (2) organiczny "kwiat" widma — gładka
 * zamknięta krzywa przez 24 pasma (nie sztywne szprychy), obracający się powoli, (3) cząsteczki
 * eksplodujące na wykryty bit, powielone kalejdoskopowo (symetria obrotowa) wokół centrum.
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
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val particles = remember { mutableStateListOf<AuroraParticle>() }
    var frameTick by remember { mutableLongStateOf(0L) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    var lastBeatCount by remember { mutableLongStateOf(beatCount) }
    var hue by remember { mutableFloatStateOf(Random.nextFloat() * 360f) }
    var rotationDeg by remember { mutableFloatStateOf(0f) }

    val bassEnergyState = rememberUpdatedState(bassEnergy)
    val overallEnergyState = rememberUpdatedState(overallEnergy)
    val beatCountState = rememberUpdatedState(beatCount)
    val bandMagnitudesState = rememberUpdatedState(bandMagnitudes)

    var trailBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var trailCanvas by remember { mutableStateOf<android.graphics.Canvas?>(null) }

    LaunchedEffect(canvasSize) {
        val width = canvasSize.width
        val height = canvasSize.height
        if (width > 0 && height > 0) {
            val bitmap = ImageBitmap(width, height)
            trailCanvas = bitmap.asAndroidBitmap().let { android.graphics.Canvas(it) }
            trailBitmap = bitmap
        }
    }

    val fadePaint = remember { android.graphics.Paint() }
    val glowPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            strokeCap = android.graphics.Paint.Cap.ROUND
            style = android.graphics.Paint.Style.STROKE
        }
    }
    val fillPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    val strokePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
        }
    }
    val particlePaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    val glowCirclePaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nowNanos ->
                val canvas = trailCanvas
                val size = canvasSize
                if (canvas == null || size.width <= 0 || size.height <= 0) {
                    frameTick = nowNanos
                    return@withFrameNanos
                }

                val dtSec = if (lastFrameNanos == 0L) {
                    0.016f
                } else {
                    ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                }
                lastFrameNanos = nowNanos
                hue = (hue + dtSec * HUE_SPEED_DEG_PER_SEC) % 360f
                rotationDeg = (rotationDeg + dtSec * ROTATION_SPEED_DEG_PER_SEC) % 360f

                if (beatCountState.value != lastBeatCount) {
                    lastBeatCount = beatCountState.value
                    spawnBurst(particles, hue, bassEnergyState.value)
                }

                val particleIterator = particles.listIterator()
                while (particleIterator.hasNext()) {
                    val particle = particleIterator.next()
                    particle.ageMs += dtSec * 1000f
                    if (particle.ageMs >= particle.lifeMs) {
                        particleIterator.remove()
                        continue
                    }
                    particle.x += particle.vx * dtSec
                    particle.y += particle.vy * dtSec
                    particle.vx *= PARTICLE_DRAG
                    particle.vy *= PARTICLE_DRAG
                }

                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val center = Offset(w / 2f, h / 2f)
                val maxRadius = min(w, h) / 2f
                val innerRadius = maxRadius * 0.18f

                // Przypalenie poprzedniej klatki zamiast czyszczenia — to jest cała "magia" smug.
                fadePaint.color = android.graphics.Color.argb(TRAIL_FADE_ALPHA, 0, 0, 0)
                canvas.drawRect(0f, 0f, w, h, fadePaint)

                drawPulseGlow(canvas, center, maxRadius, overallEnergyState.value, bassEnergyState.value, hue, glowCirclePaint)
                drawSpectrumBlossom(
                    canvas = canvas,
                    center = center,
                    innerRadius = innerRadius,
                    maxRadius = maxRadius,
                    bandMagnitudes = bandMagnitudesState.value,
                    hue = hue,
                    rotationDeg = rotationDeg,
                    fillPaint = fillPaint,
                    strokePaint = strokePaint,
                    glowPaint = glowPaint,
                )
                drawKaleidoParticles(canvas, center, particles, particlePaint)

                frameTick = nowNanos
            }
        }
    }

    Canvas(modifier = modifier.onSizeChanged { canvasSize = it }) {
        frameTick
        trailBitmap?.let { bitmap -> drawImage(bitmap) }
    }
}

private class AuroraParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var ageMs: Float,
    val lifeMs: Float,
    val hue: Float,
    val startRadius: Float,
)

private fun spawnBurst(particles: MutableList<AuroraParticle>, baseHue: Float, bassEnergy: Float) {
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
                hue = (baseHue + Random.nextFloat() * 80f - 40f + 360f) % 360f,
                startRadius = PARTICLE_MIN_RADIUS + Random.nextFloat() * PARTICLE_RADIUS_RANGE,
            ),
        )
    }
    while (particles.size > MAX_PARTICLES) {
        particles.removeAt(0)
    }
}

private fun hsvColor(hue: Float, saturation: Float, value: Float, alpha: Int): Int {
    val rgb = android.graphics.Color.HSVToColor(
        floatArrayOf(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)),
    )
    return (rgb and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}

private fun drawPulseGlow(
    canvas: android.graphics.Canvas,
    center: Offset,
    maxRadius: Float,
    overallEnergy: Float,
    bassEnergy: Float,
    hue: Float,
    paint: android.graphics.Paint,
) {
    val pulse = (overallEnergy * 0.5f + bassEnergy * 0.5f).coerceIn(0f, 1f)
    val radius = maxRadius * (0.5f + pulse * 0.9f)
    if (radius <= 0f) return
    paint.style = android.graphics.Paint.Style.FILL
    paint.shader = RadialGradient(
        center.x,
        center.y,
        radius,
        hsvColor(hue, 0.85f, 1f, (255 * (0.10f + 0.35f * pulse)).toInt()),
        android.graphics.Color.TRANSPARENT,
        Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(center.x, center.y, radius, paint)
    paint.shader = null
}

/** Gładki, zamknięty "kwiat" przez 24 punkty widma (quadratic Bezier przez midpointy) zamiast sztywnych szprych. */
private fun drawSpectrumBlossom(
    canvas: android.graphics.Canvas,
    center: Offset,
    innerRadius: Float,
    maxRadius: Float,
    bandMagnitudes: FloatArray,
    hue: Float,
    rotationDeg: Float,
    fillPaint: android.graphics.Paint,
    strokePaint: android.graphics.Paint,
    glowPaint: android.graphics.Paint,
) {
    val bandCount = bandMagnitudes.size
    if (bandCount < 3) return

    val points = Array(bandCount) { i ->
        val angle = (i / bandCount.toFloat()) * 2f * PI_F
        val magnitude = bandMagnitudes[i].coerceIn(0f, 1f)
        val radius = innerRadius + (maxRadius - innerRadius) * magnitude
        Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
    }

    val path = android.graphics.Path()
    val firstMid = Offset((points[0].x + points[bandCount - 1].x) / 2f, (points[0].y + points[bandCount - 1].y) / 2f)
    path.moveTo(firstMid.x, firstMid.y)
    for (i in 0 until bandCount) {
        val current = points[i]
        val next = points[(i + 1) % bandCount]
        val mid = Offset((current.x + next.x) / 2f, (current.y + next.y) / 2f)
        path.quadTo(current.x, current.y, mid.x, mid.y)
    }
    path.close()

    canvas.save()
    canvas.rotate(rotationDeg, center.x, center.y)

    fillPaint.shader = RadialGradient(
        center.x,
        center.y,
        maxRadius,
        hsvColor(hue, 0.85f, 1f, 60),
        hsvColor((hue + 50f) % 360f, 0.9f, 1f, 10),
        Shader.TileMode.CLAMP,
    )
    canvas.drawPath(path, fillPaint)
    fillPaint.shader = null

    val glowRadiusPx = (maxRadius - innerRadius) * 0.08f
    glowPaint.strokeWidth = (maxRadius - innerRadius) * 0.04f
    glowPaint.maskFilter = BlurMaskFilter(glowRadiusPx.coerceAtLeast(4f), BlurMaskFilter.Blur.NORMAL)
    glowPaint.color = hsvColor(hue, 0.8f, 1f, 200)
    canvas.drawPath(path, glowPaint)

    strokePaint.color = hsvColor(hue, 0.6f, 1f, 235)
    canvas.drawPath(path, strokePaint)

    canvas.restore()
}

private fun drawKaleidoParticles(
    canvas: android.graphics.Canvas,
    center: Offset,
    particles: List<AuroraParticle>,
    paint: android.graphics.Paint,
) {
    for (particle in particles) {
        val lifeRatio = (particle.ageMs / particle.lifeMs).coerceIn(0f, 1f)
        val alpha = 1f - lifeRatio
        if (alpha <= 0f) continue
        val radius = particle.startRadius * (1f - lifeRatio * 0.5f)
        if (radius <= 0f) continue

        val distance = hypot(particle.x, particle.y)
        val baseAngle = atan2(particle.y, particle.x)
        paint.color = hsvColor(particle.hue, 0.9f, 1f, (alpha * 255).toInt())

        for (slice in 0 until KALEIDOSCOPE_SLICES) {
            val angle = baseAngle + slice * (2f * PI_F / KALEIDOSCOPE_SLICES)
            val x = center.x + cos(angle) * distance
            val y = center.y + sin(angle) * distance
            canvas.drawCircle(x, y, radius, paint)
        }
    }
}

private val PI_F = PI.toFloat()

private const val KALEIDOSCOPE_SLICES = 6
private const val HUE_SPEED_DEG_PER_SEC = 16f
private const val ROTATION_SPEED_DEG_PER_SEC = 6f

/** 0..255 — ile z poprzedniej klatki "spala się" (mniej = dłuższe smugi). */
private const val TRAIL_FADE_ALPHA = 34

private const val MAX_PARTICLES = 220
private const val PARTICLES_PER_BEAT = 26
private const val PARTICLE_MIN_SPEED = 220f
private const val PARTICLE_SPEED_RANGE = 620f
private const val PARTICLE_MIN_LIFE_MS = 650f
private const val PARTICLE_LIFE_RANGE_MS = 700f
private const val PARTICLE_MIN_RADIUS = 4f
private const val PARTICLE_RADIUS_RANGE = 9f
private const val PARTICLE_DRAG = 0.965f
