package com.aurora.player.eq

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos

/**
 * Filtr biquad typu "peaking EQ" wg formuł RBJ Audio Cookbook — patrz DESIGN.md sekcja 4.1.
 * Jedna instancja = jedno pasmo dla jednego kanału audio (stan a1/a2/z1/z2 jest per-kanał,
 * więc EqualizerAudioProcessor trzyma osobną siatkę filtrów na kanał).
 *
 * Direct Form I, współczynniki znormalizowane przez a0 (a0 dzieli wszystkie pozostałe,
 * dzięki czemu nie trzeba go przechowywać ani dzielić przy każdej próbce).
 */
class BiquadFilter {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    // Historia próbek (Direct Form I): x[n-1], x[n-2], y[n-1], y[n-2]
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    init {
        updateCoefficients(sampleRateHz = 48000f, centerFreqHz = 1000f, gainDb = 0f, q = 1f)
    }

    fun updateCoefficients(sampleRateHz: Float, centerFreqHz: Float, gainDb: Float, q: Float) {
        if (gainDb == 0f) {
            // Flat: przepuść sygnał bez zmian (unika osobliwości przy A=1 i utrzymuje dokładny "0dB").
            b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
            return
        }

        val a = 10f.pow(gainDb / 40f)
        val omega = 2f * PI.toFloat() * (centerFreqHz / sampleRateHz).coerceIn(0.0001f, 0.4999f)
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2f * q)

        val a0 = 1f + alpha / a
        b0 = (1f + alpha * a) / a0
        b1 = (-2f * cosOmega) / a0
        b2 = (1f - alpha * a) / a0
        a1 = (-2f * cosOmega) / a0
        a2 = (1f - alpha / a) / a0
    }

    /**
     * BPF "constant 0 dB peak gain" wg RBJ Audio Cookbook — używane przez wizualizer widma
     * (patrz DESIGN.md, `AudioVisualizerAnalyzer`), nie przez equalizer. Formuła zweryfikowana
     * 1:1 z tekstem cookbooka tak samo jak peakingEQ w [updateCoefficients].
     */
    fun updateBandpassCoefficients(sampleRateHz: Float, centerFreqHz: Float, q: Float) {
        val omega = 2f * PI.toFloat() * (centerFreqHz / sampleRateHz).coerceIn(0.0001f, 0.4999f)
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2f * q)

        val a0 = 1f + alpha
        b0 = alpha / a0
        b1 = 0f
        b2 = -alpha / a0
        a1 = (-2f * cosOmega) / a0
        a2 = (1f - alpha) / a0
    }

    fun process(input: Float): Float {
        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
    }

    fun resetHistory() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }
}
