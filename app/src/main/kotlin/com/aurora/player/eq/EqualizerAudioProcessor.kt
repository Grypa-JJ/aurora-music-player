package com.aurora.player.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.aurora.player.domain.model.EqDefaults
import com.aurora.player.domain.model.EqState
import com.aurora.player.domain.repository.EqRepository
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 [BaseAudioProcessor] wstawiony w pipeline ExoPlayera (przez
 * [DefaultAudioSink.Builder.setAudioProcessors]) — kaskada filtrów [BiquadFilter] w czystym
 * Kotlinie, działająca identycznie na każdym urządzeniu (patrz DESIGN.md sekcja 4.1: stockowy
 * `android.media.audiofx.Equalizer` odrzucony bo liczba/częstotliwości pasm zależą od HAL vendora).
 *
 * Czyta [EqRepository.eqState] bezpośrednio na wątku audio przy każdym buforze — StateFlow.value
 * to zwykły odczyt pola (bez blokowania), więc jest to bezpieczne i tanie w gorącej pętli.
 */
class EqualizerAudioProcessor(
    private val eqRepository: EqRepository,
) : BaseAudioProcessor() {

    // [kanał][pasmo]
    private var channelFilters: Array<Array<BiquadFilter>> = emptyArray()
    private var appliedBandGains: List<Float> = emptyList()
    private var appliedEnabled = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        buildFilters(inputAudioFormat.channelCount)
        applyCoefficients(inputAudioFormat.sampleRate, eqRepository.eqState.value)
        return inputAudioFormat
    }

    private fun buildFilters(channelCount: Int) {
        val bandCount = EqDefaults.FREQUENCIES_HZ.size
        channelFilters = Array(channelCount) { Array(bandCount) { BiquadFilter() } }
        appliedBandGains = emptyList()
    }

    private fun applyCoefficients(sampleRateHz: Int, state: EqState) {
        val gains = state.bands.map { it.gainDb }
        if (gains == appliedBandGains) return
        for (channel in channelFilters) {
            for (bandIndex in channel.indices) {
                val band = state.bands.getOrNull(bandIndex) ?: continue
                channel[bandIndex].updateCoefficients(
                    sampleRateHz = sampleRateHz.toFloat(),
                    centerFreqHz = band.frequencyHz.toFloat(),
                    gainDb = band.gainDb,
                    q = BandQ,
                )
            }
        }
        appliedBandGains = gains
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val state = eqRepository.eqState.value
        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)

        if (!state.enabled || channelFilters.isEmpty()) {
            if (appliedEnabled) resetFilterHistory()
            appliedEnabled = false
            output.put(inputBuffer)
            output.flip()
            return
        }
        appliedEnabled = true

        applyCoefficients(inputAudioFormat.sampleRate, state)

        val inputShorts = inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val outputShorts = output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val channelCount = channelFilters.size
        var channel = 0
        while (inputShorts.hasRemaining()) {
            var sample = inputShorts.get() / SHORT_SCALE
            for (band in channelFilters[channel]) {
                sample = band.process(sample)
            }
            val clamped = sample.coerceIn(-1f, 1f)
            outputShorts.put((clamped * SHORT_SCALE).toInt().toShort())
            channel = (channel + 1) % channelCount
        }

        inputBuffer.position(inputBuffer.limit())
        output.position(outputShorts.position() * 2)
        output.flip()
    }

    private fun resetFilterHistory() {
        for (channel in channelFilters) {
            for (band in channel) band.resetHistory()
        }
    }

    override fun onReset() {
        channelFilters = emptyArray()
        appliedBandGains = emptyList()
    }

    private companion object {
        const val BandQ = 1f
        const val SHORT_SCALE = 32768f
    }
}
