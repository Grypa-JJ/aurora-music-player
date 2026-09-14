package com.aurora.player.eq

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nikt w tym środowisku nie może fizycznie przesłuchać efektu EQ na słuchawkach (patrz DESIGN.md
 * Etap 2, "do zweryfikowania na słuchawkach") — te testy to najbliższy zamiennik: traktują
 * [BiquadFilter] jako czarną skrzynkę, przepuszczają przez nią prawdziwe sinusoidy i MIERZĄ
 * rzeczywistą odpowiedź częstotliwościową (RMS wyjścia względem wejścia), zamiast tylko
 * odczytywać współczynniki. Złapałyby błąd typu odwrócony znak/zamieniony a1↔b1/źle użyte Q,
 * nawet gdyby wzór "wyglądał dobrze na oko" przy przepisywaniu z cookbooka.
 */
class BiquadFilterTest {

    private val sampleRateHz = 48_000f

    /** Mierzy rzeczywisty zysk (dB) filtra dla sinusoidy o [frequencyHz] w stanie ustalonym. */
    private fun measureGainDb(filter: BiquadFilter, frequencyHz: Float): Double {
        val omega = 2.0 * PI * frequencyHz / sampleRateHz
        var n = 0

        // Warmup: pozwól opaść stanowi przejściowemu IIR, zanim zaczniemy mierzyć.
        repeat(WARMUP_SAMPLES) {
            filter.process(sin(omega * n).toFloat())
            n++
        }

        var sumSquares = 0.0
        repeat(MEASURE_SAMPLES) {
            val output = filter.process(sin(omega * n).toFloat())
            sumSquares += output.toDouble() * output.toDouble()
            n++
        }

        val outputRms = sqrt(sumSquares / MEASURE_SAMPLES)
        val inputRms = 1.0 / sqrt(2.0) // RMS sinusoidy o amplitudzie 1
        return 20.0 * log10(outputRms / inputRms)
    }

    @Test
    fun `peaking EQ boost measures the configured gain at center frequency`() {
        val filter = BiquadFilter()
        filter.updateCoefficients(sampleRateHz, centerFreqHz = 1000f, gainDb = 6f, q = 1f)

        val measured = measureGainDb(filter, 1000f)

        assertEquals(6.0, measured, 0.2)
    }

    @Test
    fun `peaking EQ cut measures the configured attenuation at center frequency`() {
        val filter = BiquadFilter()
        filter.updateCoefficients(sampleRateHz, centerFreqHz = 1000f, gainDb = -9f, q = 1f)

        val measured = measureGainDb(filter, 1000f)

        assertEquals(-9.0, measured, 0.2)
    }

    @Test
    fun `peaking EQ boost decays back toward 0dB far from center frequency`() {
        val filter = BiquadFilter()
        filter.updateCoefficients(sampleRateHz, centerFreqHz = 1000f, gainDb = 12f, q = 1f)

        // ~4.3 oktawy poniżej środka (Q=1) — poza pasmem podbicia, powinno wrócić blisko 0dB,
        // a nie zostać na +12dB (to odróżnia peaking EQ od np. shelfa, który by tak nie spadł).
        val measuredFarBelow = measureGainDb(filter, 50f)

        assertEquals(0.0, measuredFarBelow, 1.0)
    }

    @Test
    fun `gainDb of zero is an exact bypass, not an approximation`() {
        val filter = BiquadFilter()
        filter.updateCoefficients(sampleRateHz, centerFreqHz = 1000f, gainDb = 0f, q = 1f)

        val input = FloatArray(64) { sin(2.0 * PI * 440.0 * it / sampleRateHz).toFloat() }
        val output = input.map { filter.process(it) }

        input.indices.forEach { i ->
            assertEquals(input[i].toDouble(), output[i].toDouble(), 1e-6)
        }
    }

    @Test
    fun `bandpass mode has near-0dB gain at its own center frequency`() {
        val filter = BiquadFilter()
        // Te same parametry co pasma widmowe w AudioVisualizerAnalyzer (BAND_Q = 4f).
        filter.updateBandpassCoefficients(sampleRateHz, centerFreqHz = 1000f, q = 4f)

        val measured = measureGainDb(filter, 1000f)

        // "Constant 0 dB peak gain" wg RBJ cookbook — nazwa wprost obiecuje ten wynik.
        assertEquals(0.0, measured, 0.3)
    }

    @Test
    fun `bandpass mode rejects frequencies well outside its passband`() {
        val filter = BiquadFilter()
        filter.updateBandpassCoefficients(sampleRateHz, centerFreqHz = 1000f, q = 4f)

        val measuredAtOctaveBelow = measureGainDb(filter, 500f)
        val measuredAtOctaveAbove = measureGainDb(filter, 2000f)

        // Realny band-pass musi odrzucać z dala od środka - nie all-pass w przebraniu.
        assert(measuredAtOctaveBelow < -6.0) {
            "Spodziewano się odrzucenia poniżej -6dB przy 500Hz (oktawa poniżej środka 1000Hz), zmierzono ${measuredAtOctaveBelow}dB"
        }
        assert(measuredAtOctaveAbove < -6.0) {
            "Spodziewano się odrzucenia poniżej -6dB przy 2000Hz (oktawa powyżej środka 1000Hz), zmierzono ${measuredAtOctaveAbove}dB"
        }
    }

    private companion object {
        const val WARMUP_SAMPLES = 16_384
        const val MEASURE_SAMPLES = 16_384
    }
}
