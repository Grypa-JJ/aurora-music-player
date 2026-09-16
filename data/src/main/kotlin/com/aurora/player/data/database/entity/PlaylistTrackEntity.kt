package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Wpis "utwór w playliście" — [trackId] to [com.aurora.player.domain.model.Track.id] (lokalny
 * MediaStore albo hash chmurowy z TrackIdHasher), NIE osobna kopia metadanych; rozwiązywane na
 * pełny Track dopiero w UI, tak samo jak planowana nakładka wzbogacania z Etapu 11.
 * `onDelete = CASCADE` — usunięcie playlisty sprząta jej wpisy bez osobnej logiki w repozytorium.
 */
@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
    val addedAtMs: Long,
)
