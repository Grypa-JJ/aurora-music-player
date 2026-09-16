package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache realnych parametrów audio odczytanych z kontenera pliku — DESIGN.md Etap 29. Wszystkie
 * cztery pola `null` = ekstraktor faktycznie nic nie znalazł (odtwarzacz nie zgaduje) — mimo to
 * wiersz istnieje z ustawionym [extractedAtMs], więc appka nie odczytuje tego samego pliku
 * ponownie przy każdym ładowaniu biblioteki.
 */
@Entity(tableName = "track_audio_metadata")
data class TrackAudioMetadataEntity(
    @PrimaryKey val trackId: Long,
    val codec: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val extractedAtMs: Long,
)
