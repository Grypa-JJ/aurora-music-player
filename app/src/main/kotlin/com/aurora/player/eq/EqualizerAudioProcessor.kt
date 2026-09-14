package com.aurora.player.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.aurora.player.domain.model.EqDefaults
import com.aurora.player.domain.model.EqState
import com.aurora.player.domain.repository.EqRepository
import com.aurora.player.projectm.ProjectMPcmBridge
import com.aurora.player.visualizer.AudioVisualizerAnalyzer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Media3 [BaseAudioProcessor] wstawiony w pipeline ExoPlayera (przez
 * [DefaultAudioSink.Builder.setAudioProcessors]) — kaskada filtrów [BiquadFilter] w czystym
 * Kotlinie, działająca identycznie na każdym urządzeniu (patrz DESIGN.md sekcja 4.1: stockowy
 * `android.media.audiofx.Equalizer` odrzucony bo liczba/częstotliwości pasm zależą od HAL vendora).
 *
 * Czyta [EqRepository.eqState] bezpośrednio na wątku audio przy każdym buforze — StateFlow.value
 * to zwykły odczyt pola (bez blokowania), więc jest to bezpieczne i tanie w gorącej pętli.
 *
 * Przy okazji zasila [AudioVisualizerAnalyzer] próbką zmiksowaną do mono (po EQ, czyli dokładnie
 * to, co faktycznie słychać) — jeden przebieg po buforze robi obie rzeczy naraz, więc wizualizer
 * nie kosztuje osobnego przejścia po danych PCM. Ta sama zmiksowana do mono próbka trafia też do
 * [ProjectMPcmBridge] (Etap 9, DESIGN.md) — projectM dostaje dokładnie to samo źródło co stary
 * wizualizer widmowy, tylko w formacie surowego PCM zamiast już policzonych pasm. Gdy nikt nie
 * słucha (ekran wizualizera niewidoczny), `dispatch()` jest tanim no-opem.
 */
class EqualizerAudioProcessor(
    private val eqRepository: EqRepository,
    private val visualizerAnalyzer: AudioVisualizerAnalyzer,
) : BaseAudioProcessor() {

    // [kanał][pasmo]
    private var channelFilters: Array<Array<BiquadFilter>> = emptyArray()
    private var appliedBandGains: List<Float> = emptyList()
    private var appliedEnabled = false
    private var projectMPcmScratch: ShortArray = ShortArray(0)

    // Kompensacja headroomu — zgłoszenie: "equalizer jest zbyt mocny, ustawienie trybu
    // powoduje trzeszczenie głośników". 10 kaskadowo połączonych filtrów peaking (Q=1, rozstaw
    // ~1 oktawy) ma mocno nakładające się zbocza — kilka sąsiednich pasm podbitych naraz (np.
    // "Bass Boost": +6/+5/+4/+2dB) daje w paśmie nakładania się WIĘKSZE realne wzmocnienie niż
    // jakiekolwiek pojedyncze pasmo, bez żadnej korekty sygnał regularnie przekraczał 0dBFS i
    // trafiał w twarde obcięcie (`coerceIn`) poniżej — właśnie to słychać jako trzask.
    private var preampLinearGain = 1f

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
        preampLinearGain = computePreampLinearGain(gains)
    }

    /**
     * Szacunek headroomu potrzebnego, żeby nakładające się podbite pasma nie przycinały sygnału.
     * Połowa sumy DODATNICH wzmocnień — nie pełna suma (zbyt pesymistyczne: zbocza filtrów nie
     * nakładają się w 100%, więc realny wspólny szczyt jest niższy niż suma wszystkich pasm) ani
     * tylko pojedynczy max (za mało: nie uwzględnia kilku sąsiednich podbitych pasm naraz, jak w
     * "Bass Boost"). Współczynnik 0.5 dobrany na presetach z [EqPresets] — do doregulowania,
     * gdyby któryś preset nadal przycinał.
     */
    private fun computePreampLinearGain(gainsDb: List<Float>): Float {
        val positiveSum = gainsDb.filter { it > 0f }.sum()
        if (positiveSum <= 0f) return 1f
        val headroomDb = positiveSum * 0.5f
        return 10f.pow(-headroomDb / 20f)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val state = eqRepository.eqState.value
        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)
        val channelCount = channelFilters.size
        val sampleRateHz = inputAudioFormat.sampleRate

        if (channelCount == 0) {
            // onConfigure jeszcze nie ustawił filtrów — przepuść bez zmian, nic więcej się nie da zrobić.
            output.put(inputBuffer)
            output.flip()
            return
        }

        val eqEnabled = state.enabled
        if (eqEnabled) {
            applyCoefficients(sampleRateHz, state)
        } else if (appliedEnabled) {
            resetFilterHistory()
        }
        appliedEnabled = eqEnabled

        val inputShorts = inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val outputShorts = output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        val maxFrames = inputShorts.remaining() / channelCount
        if (projectMPcmScratch.size < maxFrames) {
            projectMPcmScratch = ShortArray(maxFrames)
        }
        var frameCountInBuffer = 0

        var channel = 0
        var frameSum = 0f
        while (inputShorts.hasRemaining()) {
            var sample = inputShorts.get() / SHORT_SCALE
            if (eqEnabled) {
                for (band in channelFilters[channel]) {
                    sample = band.process(sample)
                }
                sample *= preampLinearGain
            }
            // Miękkie ograniczenie zamiast twardego `coerceIn` — zgłoszenie: "trzeszczenie
            // głośników". Poniżej progu sygnał jest bit-dokładnie nietknięty (brak zabarwienia
            // normalnego materiału); dopiero rzadkie, chwilowe przekroczenia powyżej progu (mimo
            // kompensacji preampem) dostają płynne nasycenie zamiast ostrego cyfrowego obcięcia.
            val clamped = softClip(sample)
            outputShorts.put((clamped * SHORT_SCALE).toInt().toShort())

            frameSum += clamped
            channel++
            if (channel >= channelCount) {
                val mixedDown = (frameSum / channelCount).coerceIn(-1f, 1f)
                visualizerAnalyzer.processSample(mixedDown, sampleRateHz)
                projectMPcmScratch[frameCountInBuffer] = (mixedDown * SHORT_SCALE).toInt().toShort()
                frameCountInBuffer++
                frameSum = 0f
                channel = 0
            }
        }
        visualizerAnalyzer.publishSnapshot()
        if (frameCountInBuffer > 0) {
            ProjectMPcmBridge.dispatch(projectMPcmScratch, frameCountInBuffer, channels = 1)
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
        preampLinearGain = 1f
        visualizerAnalyzer.reset()
    }

    /**
     * Poniżej [SoftClipThreshold] zwraca wejście BEZ ZMIAN (normalny materiał, nawet głośny,
     * zostaje bit-dokładnie taki jak po EQ) — dopiero powyżej progu kompresuje resztę zakresu do
     * [SoftClipThreshold, 1.0] przez tanh, więc rzadkie przekroczenie headroomu brzmi jak łagodne
     * nasycenie zamiast trzasku twardego obcięcia.
     */
    private fun softClip(x: Float): Float {
        val ax = abs(x)
        if (ax <= SoftClipThreshold) return x
        val sign = if (x < 0f) -1f else 1f
        val excess = (ax - SoftClipThreshold) / (1f - SoftClipThreshold)
        val compressed = SoftClipThreshold + (1f - SoftClipThreshold) * tanh(excess)
        return sign * compressed
    }

    private companion object {
        const val BandQ = 1f
        const val SHORT_SCALE = 32768f
        const val SoftClipThreshold = 0.85f
    }
}
