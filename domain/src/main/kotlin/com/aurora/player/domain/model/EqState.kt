package com.aurora.player.domain.model

data class EqBand(
    val frequencyHz: Int,
    val gainDb: Float,
)

data class EqState(
    val enabled: Boolean = false,
    val bands: List<EqBand> = EqDefaults.flatBands(),
    val activePresetName: String = EqPresets.FLAT,
)

object EqDefaults {
    /** 10-band ISO — patrz DESIGN.md sekcja 4.1 (dowolne pasma, nie stockowy 5-band Equalizer). */
    val FREQUENCIES_HZ = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f

    fun flatBands(): List<EqBand> = FREQUENCIES_HZ.map { EqBand(it, 0f) }
}

object EqPresets {
    const val FLAT = "Flat"
    const val BASS_BOOST = "Bass Boost"
    const val VOCAL = "Vocal"
    const val ROCK = "Rock"
    const val ELECTRONIC = "Electronic"

    /** Gainy (dB) w kolejności [EqDefaults.FREQUENCIES_HZ] — do wystrojenia empirycznie później. */
    val GAINS: Map<String, List<Float>> = linkedMapOf(
        FLAT to List(10) { 0f },
        BASS_BOOST to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        VOCAL to listOf(-3f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, -1f, -2f),
        ROCK to listOf(4f, 3f, 2f, 0f, -1f, 0f, 2f, 3f, 4f, 4f),
        ELECTRONIC to listOf(5f, 4f, 2f, 0f, -2f, 0f, 1f, 3f, 5f, 5f),
    )

    fun bandsFor(presetName: String): List<EqBand> {
        val gains = GAINS[presetName] ?: GAINS.getValue(FLAT)
        return EqDefaults.FREQUENCIES_HZ.zip(gains) { freq, gain -> EqBand(freq, gain) }
    }
}
