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
 *
 * Dodatkowo wykrywa "bity" prostym energy-based onset detection na paśmie basowym (porównanie
 * chwilowej energii z jej wygładzoną średnią kroczącą, z progiem i minimalnym odstępem między
 * bitami) — wystarczające do napędzania efektów wizualnych (wybuchy cząsteczek), nie jest to
 * precyzyjny beat-tracker do wykrywania BPM.
 */
@Singleton
class AudioVisualizerAnalyzer @Inject constructor() {

    private var bandFilters: Array<BiquadFilter> = emptyArray()
    private var sumSquares: FloatArray = FloatArray(0)
    private var sampleCount = 0
    private var configuredSampleRateHz = -1

    private var bassEnergyEma = 0f
    private var lastBeatAtMs = 0L
    private var beatCounter = 0L

    private val _frame = MutableStateFlow(VisualizerFrame(FloatArray(BAND_COUNT)))
    val frame: StateFlow<VisualizerFrame> = _frame

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

        val rawRms = FloatArray(bandFilters.size)
        for (i in sumSquares.indices) {
            rawRms[i] = sqrt(sumSquares[i] / sampleCount)
            sumSquares[i] = 0f
        }
        sampleCount = 0

        val normalized = FloatArray(rawRms.size) { normalize(rawRms[it]) }
        val bassEnergy = averageRange(normalized, BASS_RANGE)
        val midEnergy = averageRange(normalized, MID_RANGE)
        val trebleEnergy = averageRange(normalized, TREBLE_RANGE)
        val overallEnergy = if (normalized.isEmpty()) 0f else normalized.average().toFloat()

        val rawBassEnergy = averageRange(rawRms, BASS_RANGE)
        beatCounter = detectBeat(rawBassEnergy, beatCounter)

        _frame.value = VisualizerFrame(
            bandMagnitudes = normalized,
            bassEnergy = bassEnergy,
            midEnergy = midEnergy,
            trebleEnergy = trebleEnergy,
            overallEnergy = overallEnergy,
            beatCount = beatCounter,
        )
    }

    /** Gdy nic nie gra/EQ nieaktywny — sprowadź wizualizację do zera zamiast zostawić ostatnią klatkę. */
    fun reset() {
        sumSquares.fill(0f)
        sampleCount = 0
        bassEnergyEma = 0f
        _frame.value = VisualizerFrame(FloatArray(bandFilters.size))
    }

    private fun detectBeat(rawBassEnergy: Float, currentCount: Long): Long {
        bassEnergyEma = if (bassEnergyEma == 0f) {
            rawBassEnergy
        } else {
            bassEnergyEma * (1f - BEAT_EMA_ALPHA) + rawBassEnergy * BEAT_EMA_ALPHA
        }

        val now = System.currentTimeMillis()
        val isLoudEnough = rawBassEnergy > BEAT_MIN_RAW_ENERGY
        val isSpike = rawBassEnergy > bassEnergyEma * BEAT_THRESHOLD_RATIO
        val isDebounced = now - lastBeatAtMs > BEAT_MIN_INTERVAL_MS

        if (isLoudEnough && isSpike && isDebounced) {
            lastBeatAtMs = now
            return currentCount + 1
        }
        return currentCount
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

    private fun averageRange(values: FloatArray, range: IntRange): Float {
        if (values.isEmpty()) return 0f
        val from = range.first.coerceIn(0, values.lastIndex)
        val to = range.last.coerceIn(0, values.lastIndex)
        if (to < from) return 0f
        var sum = 0f
        for (i in from..to) sum += values[i]
        return sum / (to - from + 1)
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

        // Pasma 0-23 rozstawione logarytmicznie 60Hz-12kHz — pierwsze ~6 pasm to z grubsza <250Hz (bas).
        val BASS_RANGE = 0..5
        val MID_RANGE = 6..15
        val TREBLE_RANGE = 16..23

        const val BEAT_EMA_ALPHA = 0.15f
        const val BEAT_THRESHOLD_RATIO = 1.4f
        const val BEAT_MIN_RAW_ENERGY = 0.015f
        const val BEAT_MIN_INTERVAL_MS = 220L
    }
}
