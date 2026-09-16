package com.aurora.player.data.media

import com.aurora.player.data.database.dao.TrackAudioMetadataDao
import com.aurora.player.data.database.dao.TrackMetadataOverrideDao
import com.aurora.player.domain.model.AudioTrackMetadata
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.TrackRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [metadataOverrideDao] nakłada poprawki z MusicBrainz/Cover Art Archive zebrane w poprzednich
 * sesjach — DESIGN.md Etap 25/27 (tytuł/wykonawca/album ORAZ okładka, dwa niezależne kryteria,
 * patrz [com.aurora.player.data.database.entity.TrackMetadataOverrideEntity]). [audioMetadataDao]
 * nakłada codec/bitrate/sample rate/bit depth — DESIGN.md Etap 29. Oba to czysto lokalny odczyt
 * Room (bez sieci/bez otwierania plików), więc nie spowalniają zwykłego ładowania biblioteki;
 * samo WYSZUKIWANIE nowych wartości to osobny, wolniejszy proces w tle
 * ([com.aurora.player.domain.repository.MetadataEnrichmentRepository]/
 * [com.aurora.player.domain.repository.AudioMetadataRepository]), uruchamiany z ViewModelu.
 */
@Singleton
class TrackRepositoryImpl @Inject constructor(
    private val mediaStoreScanner: MediaStoreScanner,
    private val metadataOverrideDao: TrackMetadataOverrideDao,
    private val audioMetadataDao: TrackAudioMetadataDao,
) : TrackRepository {
    override suspend fun getAllTracks(): List<Track> {
        val tracks = mediaStoreScanner.scanTracks()
        val overrides = metadataOverrideDao.getAll().associateBy { it.trackId }
        val audioMetadataByTrackId = audioMetadataDao.getAll().associateBy { it.trackId }
        return tracks.map { track ->
            val override = overrides[track.id]
            val audioMetadataEntity = audioMetadataByTrackId[track.id]
            track.copy(
                title = override?.title ?: track.title,
                artist = override?.artist ?: track.artist,
                album = override?.album ?: track.album,
                albumArtUri = override?.albumArtUri ?: track.albumArtUri,
                audioMetadata = audioMetadataEntity?.let {
                    AudioTrackMetadata(
                        codec = it.codec,
                        bitrateKbps = it.bitrateKbps,
                        sampleRateHz = it.sampleRateHz,
                        bitDepth = it.bitDepth,
                    )
                },
            )
        }
    }
}
