package com.aurora.player.visualizer

import com.aurora.player.eq.BiquadFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Wizualizer widmowy à la stary Windows Media Player — bez systemowego
 * `android.media.audiofx.Visualizer` (wymagałby uprawnienia RECORD_AUDIO nawet dla własnej
 * sesji audio, patrz dokumentacja AOSP). Zamiast tego wpięty bezpośrednio w
 * [com.aurora.player.eq.EqualizerAudioProcessor], który i tak już widzi każdą próbkę PCM —
 * zero dodatkowych uprawnień, zero zależności od kolejnego frameworkowego efektu audio.
 *
 * [BAND_COUNT] pasm pasmowoprzepustowych (BiquadFilter w trybie BPF) rozstawionych logarytmicznie
 * między [MIN_FREQ_HZ] a [MAX_FREQ_HZ]; RMS energii każdego pasma liczone per bufor audio
 * (`processSample` per próbka, `publishSnapshot` raz na koniec bufora).
 */
@Singleton
class AudioVisualizerAnalyzer @Inject constructor() {

    private var bandFilters: Array<BiquadFilter> = emptyArray()
    private var sumSquares: FloatArray = FloatArray(0)
    private var sampleCount = 0
    private var configuredSampleRateHz = -1

    private val _magnitudes = MutableStateFlow(FloatArray(BAND_COUNT))
    val magnitudes: StateFlow<FloatArray> = _magnitudes

    fun processSample(monoSample: Float, sampleRateHz: Int) {
        ensureConfigured(sampleRateHz)
        for (i in bandFilters.indices) {
            val filtered = bandFilters[i].process(monoSample)
            sumSquares[i] += filtered * filtered
        }
        sampleCount++
    }

    fun publishSnapshot() {
        if (sampleCount == 0) return
        val result = FloatArray(bandFilters.size)
        for (i in sumSquares.indices) {
            val rms = sqrt(sumSquares[i] / sampleCount)
            result[i] = normalize(rms)
            sumSquares[i] = 0f
        }
        sampleCount = 0
        _magnitudes.value = result
    }

    /** Gdy nic nie gra/EQ nieaktywny — sprowadź słupki do zera zamiast zostawić ostatnią klatkę. */
    fun reset() {
        sumSquares.fill(0f)
        sampleCount = 0
        _magnitudes.value = FloatArray(bandFilters.size)
    }

    private fun ensureConfigured(sampleRateHz: Int) {
        if (sampleRateHz == configuredSampleRateHz && bandFilters.isNotEmpty()) return
        val frequencies = logSpacedFrequencies(sampleRateHz)
        bandFilters = Array(BAND_COUNT) { i ->
            BiquadFilter().apply { updateBandpassCoefficients(sampleRateHz.toFloat(), frequencies[i], BAND_Q) }
        }
        sumSquares = FloatArray(BAND_COUNT)
        sampleCount = 0
        configuredSampleRateHz = sampleRateHz
    }

    /** Kompresja logarytmiczna (dB), żeby ciche fragmenty też było widać na słupkach. */
    private fun normalize(rms: Float): Float {
        val db = 20f * log10(rms.coerceAtLeast(1e-6f))
        return ((db + 50f) / 50f).coerceIn(0f, 1f)
    }

    private fun logSpacedFrequencies(sampleRateHz: Int): FloatArray {
        val nyquist = sampleRateHz / 2f
        val maxUsable = minOf(MAX_FREQ_HZ, nyquist * 0.9f)
        val logMin = ln(MIN_FREQ_HZ)
        val logMax = ln(maxUsable)
        return FloatArray(BAND_COUNT) { i ->
            val t = i / (BAND_COUNT - 1f)
            exp(logMin + t * (logMax - logMin))
        }
    }

    private companion object {
        const val BAND_COUNT = 24
        const val BAND_Q = 4f
        const val MIN_FREQ_HZ = 60f
        const val MAX_FREQ_HZ = 12000f
    }
}
