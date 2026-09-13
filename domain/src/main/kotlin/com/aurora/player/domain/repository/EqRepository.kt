package com.aurora.player.domain.repository

import com.aurora.player.domain.model.EqState
import kotlinx.coroutines.flow.StateFlow

/**
 * Stan equalizera — jedno źródło prawdy odczytywane zarówno przez UI (suwaki), jak i przez
 * silnik DSP (`EqualizerAudioProcessor`) w wątku audio. Patrz DESIGN.md sekcja 4.2.
 */
interface EqRepository {
    val eqState: StateFlow<EqState>

    fun setEnabled(enabled: Boolean)
    fun setBandGain(bandIndex: Int, gainDb: Float)
    fun applyPreset(presetName: String)
    fun reset()
}
