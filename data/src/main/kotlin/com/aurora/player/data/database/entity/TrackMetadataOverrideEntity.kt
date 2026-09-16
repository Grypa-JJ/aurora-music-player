package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Poprawki z MusicBrainz/Cover Art Archive dla jednego utworu — DESIGN.md Etap 25/27. Dwa
 * NIEZALEŻNE kryteria kandydowania trafiają do tej samej encji: [title]/[artist]/[album] tylko
 * gdy lokalne tagi wyglądały źle ([com.aurora.player.domain.util.TrackMetadataHeuristics.looksIncomplete]),
 * [albumArtUri] tylko gdy brakowało lokalnej okładki ([TrackMetadataHeuristics.needsCoverArt]) —
 * utwór z DOBRYMI tagami, ale bez okładki, dostaje więc wpis z `albumArtUri` niepustym i
 * title/artist/album `null` (nie nadpisuj tego, co już było poprawne). Wszystkie cztery pola
 * `null` = appka już próbowała i NIE znalazła niczego (ta sama zasada "zapisz też negatyw" co
 * `lyrics_cache`) — [matchedAtMs] i tak jest ustawione, więc appka nie pyta ponownie o ten sam
 * `trackId`.
 */
@Entity(tableName = "track_metadata_override")
data class TrackMetadataOverrideEntity(
    @PrimaryKey val trackId: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtUri: String?,
    val matchedAtMs: Long,
)
