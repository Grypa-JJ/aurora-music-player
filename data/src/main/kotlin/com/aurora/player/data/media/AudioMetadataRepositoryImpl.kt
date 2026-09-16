package com.aurora.player.data.media

import com.aurora.player.data.database.dao.TrackAudioMetadataDao
import com.aurora.player.data.database.entity.TrackAudioMetadataEntity
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.repository.AudioMetadataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Łączy [TrackAudioMetadataDao] (cache, także negatywny — patrz encja) z [AudioMetadataExtractor]
 * (lokalny odczyt pliku) — DESIGN.md Etap 29. Tylko [TrackSource.LOCAL] — `MediaExtractor`
 * czytałby zdalne `https://` (Drive/WebDAV) bez nagłówków autoryzacji, którymi dysponuje tylko
 * warstwa odtwarzania w `app` (`AuthenticatingHttpDataSourceFactory`); świadomie odłożone, nie
 * przeoczone, żeby nie mieszać dwóch różnych kontraktów dostępu do pliku w jednej rundzie.
 */
@Singleton
class AudioMetadataRepositoryImpl @Inject constructor(
    private val dao: TrackAudioMetadataDao,
    private val extractor: AudioMetadataExtractor,
) : AudioMetadataRepository {

    override fun extractMissing(tracks: List<Track>): Flow<Track> = flow {
        for (track in tracks) {
            if (track.source != TrackSource.LOCAL) continue
            if (dao.get(track.id) != null) continue

            val metadata = extractor.extract(track.uri)
            dao.insert(
                TrackAudioMetadataEntity(
                    trackId = track.id,
                    codec = metadata?.codec,
                    bitrateKbps = metadata?.bitrateKbps,
                    sampleRateHz = metadata?.sampleRateHz,
                    bitDepth = metadata?.bitDepth,
                    extractedAtMs = System.currentTimeMillis(),
                ),
            )
            if (metadata != null) emit(track.copy(audioMetadata = metadata))
        }
    }
}
