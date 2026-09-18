package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache odpowiedzi TheAudioDB per wykonawca — DESIGN.md Etap 43. Klucz to znormalizowana nazwa
 * wykonawcy (trim + lowercase — appka nie ma stabilnego id wykonawcy, tylko string z tagów), nie
 * `Track.id`. Wszystkie pola `null` poza [fetchedAtMs] = appka już próbowała i nic nie znalazła
 * (ta sama zasada "zapisz też negatyw" co `track_metadata_override`/`lyrics_cache`) — appka nie
 * pyta ponownie o tego samego wykonawcy.
 */
@Entity(tableName = "artist_info")
data class ArtistInfoEntity(
    @PrimaryKey val artistNameKey: String,
    val biography: String?,
    val genre: String?,
    val style: String?,
    val mood: String?,
    val bannerUrl: String?,
    val thumbUrl: String?,
    val fetchedAtMs: Long,
)
