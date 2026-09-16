package com.aurora.player.domain.audio

/**
 * Sterowanie JEDNYM wyjściem audio (`connect()`/`disconnect()`) — celowo poza pakietem
 * `repository/`: żadne z sześciu istniejących repozytoriów tego projektu nie ma tej semantyki
 * (sterowanie urządzeniem, nie dostęp do danych), więc odejście od sufiksu `*Repository` jest tu
 * świadome. Patrz DESIGN.md Etap 21 — przygotowanie appki pod zewnętrzne DAC-i (USB/BLE/Aurelis)
 * bez pisania protokołów dla nieistniejącego jeszcze sprzętu.
 *
 * `domain` zostaje czystym Kotlinem (ten sam wymóg, który [com.aurora.player.domain.repository.CloudLibraryRepository]
 * dokumentuje dla `signIn()`) — zero `android.media.AudioDeviceInfo` w tej sygnaturze.
 */
interface AudioOutput {
    val capabilities: AudioCapabilities

    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun setVolume(value: Float)

    // Platforma nie gwarantuje żadnej z poniższych operacji (patrz [BitPerfectStatus]) — `Boolean`
    // zamiast `Unit`, żeby ten interfejs fizycznie nie mógł udawać sukcesu, którego nie ma.
    suspend fun setSampleRate(sampleRate: Int): Boolean
    suspend fun setGain(gain: Gain): Boolean
    suspend fun setFilter(filter: DacFilter): Boolean
}

/**
 * Zdolności jednego wyjścia audio. Domyślnie same "nie" — świadomy model realnych ograniczeń
 * platformy (patrz DESIGN.md Etap 21), nie skrót do wypełnienia później. Audio Lab (DESIGN.md
 * Etap 20h) ma czytać ten model wprost zamiast wymyślać własny status DAC-a.
 */
data class AudioCapabilities(
    val deviceName: String? = null,
    val supportsVolumeControl: Boolean = false,
    val supportsSampleRateControl: Boolean = false,
    val supportsGainControl: Boolean = false,
    val supportsFilterControl: Boolean = false,
    val bitPerfectStatus: BitPerfectStatus = BitPerfectStatus.UNKNOWN,
)

enum class BitPerfectStatus { UNKNOWN, LIKELY_RESAMPLED, GUARANTEED_BY_SYSTEM }

enum class DacFilter { UNSUPPORTED } // realne warianty dopiero z pierwszym urządzeniem, które je definiuje

data class Gain(val value: Float) // jednostka/zakres nieznane bez realnego DAC-a
