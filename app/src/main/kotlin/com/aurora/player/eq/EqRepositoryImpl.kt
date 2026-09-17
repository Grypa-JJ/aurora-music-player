package com.aurora.player.eq

import com.aurora.player.data.database.dao.EqStateDao
import com.aurora.player.data.database.entity.EqStateEntity
import com.aurora.player.di.ApplicationScope
import com.aurora.player.domain.model.EqBand
import com.aurora.player.domain.model.EqDefaults
import com.aurora.player.domain.model.EqPresets
import com.aurora.player.domain.model.EqState
import com.aurora.player.domain.repository.EqRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Etap 20d: trwały zapis EQ w Room — [EqStateEntity] to zawsze jeden wiersz (`id = 0`), appka ma
 * jeden globalny EQ. `MutableStateFlow` zostaje jedynym źródłem prawdy dla UI/DSP (bez zmian w
 * ich sposobie czytania), Room to tylko backing store: wczytywany raz w `init`, zapisywany przy
 * KAŻDEJ zmianie ale z `debounce(500ms)` — bez tego przeciąganie suwaka w [EqualizerSheet]
 * (`onValueChange`, nie `onValueChangeFinished`) waliłoby Room dziesiątkami zapisów na sekundę.
 */
@OptIn(FlowPreview::class)
@Singleton
class EqRepositoryImpl @Inject constructor(
    private val eqStateDao: EqStateDao,
    @ApplicationScope private val scope: CoroutineScope,
) : EqRepository {

    private val _eqState = MutableStateFlow(EqState())
    override val eqState: StateFlow<EqState> = _eqState

    init {
        scope.launch {
            eqStateDao.get()?.toDomain()?.let { _eqState.value = it }
        }
        scope.launch {
            eqState.debounce(500).collect { state -> eqStateDao.upsert(state.toEntity()) }
        }
    }

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

    private fun EqState.toEntity() = EqStateEntity(
        enabled = enabled,
        activePresetName = activePresetName,
        gainsDbCsv = bands.joinToString(",") { it.gainDb.toString() },
    )

    private fun EqStateEntity.toDomain(): EqState {
        val gains = gainsDbCsv.split(",").mapNotNull { it.toFloatOrNull() }
        val bands = if (gains.size == EqDefaults.FREQUENCIES_HZ.size) {
            EqDefaults.FREQUENCIES_HZ.zip(gains) { freq, gain -> EqBand(freq, gain) }
        } else {
            EqDefaults.flatBands()
        }
        return EqState(enabled = enabled, bands = bands, activePresetName = activePresetName)
    }
}
