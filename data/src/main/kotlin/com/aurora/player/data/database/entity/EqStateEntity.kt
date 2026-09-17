package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Trwały stan equalizera — DESIGN.md Etap 20d. Zawsze dokładnie jeden wiersz (`id = 0`), bo
 * appka ma jeden globalny EQ, nie per-utwór/per-playlistę. Pasma trzymane jako CSV gainów (dB)
 * w kolejności [com.aurora.player.domain.model.EqDefaults.FREQUENCIES_HZ] zamiast osobnej,
 * znormalizowanej tabeli — lista częstotliwości jest stała (10 pasm ISO), więc normalizacja nie
 * dodaje niczego poza narzutem JOIN-a.
 */
@Entity(tableName = "eq_state")
data class EqStateEntity(
    @PrimaryKey val id: Int = 0,
    val enabled: Boolean,
    val activePresetName: String,
    val gainsDbCsv: String,
)
