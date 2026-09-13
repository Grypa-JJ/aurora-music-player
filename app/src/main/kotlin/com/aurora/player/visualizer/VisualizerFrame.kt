package com.aurora.player.visualizer

/**
 * Jedna klatka danych wizualizera — patrz [AudioVisualizerAnalyzer]. [bandMagnitudes] to 24
 * znormalizowane (0..1) wartości widma; [beatCount] rośnie o 1 przy każdym wykrytym uderzeniu
 * basu — UI porównuje go między klatkami (nie sam bit jako Boolean), żeby nie przegapić bitu
 * między dwoma odczytami stanu.
 */
data class VisualizerFrame(
    val bandMagnitudes: FloatArray = FloatArray(24),
    val bassEnergy: Float = 0f,
    val midEnergy: Float = 0f,
    val trebleEnergy: Float = 0f,
    val overallEnergy: Float = 0f,
    val beatCount: Long = 0L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisualizerFrame) return false
        return bandMagnitudes.contentEquals(other.bandMagnitudes) &&
            bassEnergy == other.bassEnergy &&
            midEnergy == other.midEnergy &&
            trebleEnergy == other.trebleEnergy &&
            overallEnergy == other.overallEnergy &&
            beatCount == other.beatCount
    }

    override fun hashCode(): Int {
        var result = bandMagnitudes.contentHashCode()
        result = 31 * result + bassEnergy.hashCode()
        result = 31 * result + midEnergy.hashCode()
        result = 31 * result + trebleEnergy.hashCode()
        result = 31 * result + overallEnergy.hashCode()
        result = 31 * result + beatCount.hashCode()
        return result
    }
}
