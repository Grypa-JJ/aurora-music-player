package com.aurora.player.eq

import com.aurora.player.domain.model.EqDefaults
import com.aurora.player.domain.model.EqPresets
import com.aurora.player.domain.model.EqState
import com.aurora.player.domain.repository.EqRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Etap 2: stan w pamięci (singleton przez cały czas życia procesu) — wystarcza, bo
 * EqualizerAudioProcessor i UI czytają ten sam StateFlow. Trwały zapis do Room
 * (zapamiętanie po restarcie appki) dojdzie razem z tabelami Genius w etapie 3
 * (DESIGN.md sekcja 4.2 przewiduje EqPresetEntity/EqStateEntity).
 */
@Singleton
class EqRepositoryImpl @Inject constructor() : EqRepository {

    private val _eqState = MutableStateFlow(EqState())
    override val eqState: StateFlow<EqState> = _eqState

    override fun setEnabled(enabled: Boolean) {
        _eqState.update { it.copy(enabled = enabled) }
    }

    override fun setBandGain(bandIndex: Int, gainDb: Float) {
        _eqState.update { state ->
            val clamped = gainDb.coerceIn(EqDefaults.MIN_GAIN_DB, EqDefaults.MAX_GAIN_DB)
            val updatedBands = state.bands.toMutableList().also { bands ->
                if (bandIndex in bands.indices) {
                    bands[bandIndex] = bands[bandIndex].copy(gainDb = clamped)
                }
            }
            state.copy(bands = updatedBands, activePresetName = "Custom")
        }
    }

    override fun applyPreset(presetName: String) {
        _eqState.update { it.copy(bands = EqPresets.bandsFor(presetName), activePresetName = presetName) }
    }

    override fun reset() {
        _eqState.update { it.copy(bands = EqDefaults.flatBands(), activePresetName = EqPresets.FLAT) }
    }
}
