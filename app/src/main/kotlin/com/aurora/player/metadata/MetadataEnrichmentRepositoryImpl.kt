package com.aurora.player.metadata

import com.aurora.player.data.database.dao.TrackMetadataOverrideDao
import com.aurora.player.data.database.entity.TrackMetadataOverrideEntity
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.LocalAlbumArtRepository
import com.aurora.player.domain.repository.MetadataEnrichmentRepository
import com.aurora.player.domain.util.TrackMetadataHeuristics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Łączy [TrackMetadataOverrideDao] (cache, także negatywny — patrz encja) z [MusicBrainzClient]/
 * [CoverArtArchiveClient] (sieć, rate-limited) i [LocalAlbumArtRepository] (lokalnie, szybko) —
 * DESIGN.md Etap 25/28/44. DWA niezależne kryteria kandydowania trafiają do tej pętli: złe tagi
 * ([TrackMetadataHeuristics.looksIncomplete]) i/lub brak okładki
 * ([TrackMetadataHeuristics.needsCoverArt]) — utwór z dobrymi tagami, ale bez okładki, dostaje
 * zapytanie o samą okładkę i NIGDY nie nadpisuje tytułu/wykonawcy, które już były poprawne.
 * Utwór już kiedyś sprawdzony (sukces LUB porażka) jest pomijany bez zapytania — poprawiona
 * wartość z poprzedniej sesji i tak jest już widoczna przez
 * [com.aurora.player.data.media.TrackRepositoryImpl.getAllTracks]. Appka nigdy NIE pyta o
 * potwierdzenie — auto-zastosowanie świadome (patrz DESIGN.md Etap 28).
 *
 * Etap 44, zgłoszenie: lokalna okładka ([LocalAlbumArtRepository]) i sieciowa kiedyś biegły jako
 * DWA NIEZALEŻNE przebiegi po całej bibliotece — dawało to realny wyścig (sieć potrafiła
 * nadpisać dobrą lokalną okładkę, zanim ta zdążyła się rozwiązać) ALBO, po naiwnej poprawce
 * "poczekaj na lokalne PRZED sieciowym", zauważalne opóźnienie całego wzbogacania biblioteki
 * (lokalne niepowodzenia na WSZYSTKICH utworach musiały się skończyć, zanim ruszyło cokolwiek
 * sieciowego, nawet dla utworów niepowiązanych). Poprawka: JEDEN wspólny potok, lokalnie per
 * utwór PRZED siecią dla TEGO SAMEGO utworu — koszt lokalnej próby (ograniczony, bo
 * [LocalAlbumArtRepository] ma twardy timeout) jest teraz częścią tej samej, i tak już
 * sekwencyjnej (limit MusicBrainz ~1/s) pętli, nie osobną fazą blokującą wszystko inne. Dodatkowa
 * korzyść: jeśli lokalna okładka się znajdzie, appka w ogóle NIE odpytuje MusicBrainz o samą
 * okładkę (tylko jeśli tagi tekstowe też są złe).
 */
@Singleton
class MetadataEnrichmentRepositoryImpl @Inject constructor(
    private val dao: TrackMetadataOverrideDao,
    private val client: MusicBrainzClient,
    private val coverArtClient: CoverArtArchiveClient,
    private val localAlbumArtRepository: LocalAlbumArtRepository,
) : MetadataEnrichmentRepository {

    override fun enrichLibrary(tracks: List<Track>): Flow<Track> = flow {
        val candidates = tracks.filter {
            TrackMetadataHeuristics.looksIncomplete(it) || TrackMetadataHeuristics.needsCoverArt(it)
        }
        for (track in candidates) {
            if (dao.get(track.id) != null) continue

            val hasBadTags = TrackMetadataHeuristics.looksIncomplete(track)

            val localCoverArtUrl = if (TrackMetadataHeuristics.needsCoverArt(track)) {
                localAlbumArtRepository.resolveOne(track)
            } else {
                null
            }
            val stillNeedsNetworkCoverArt = localCoverArtUrl == null && TrackMetadataHeuristics.needsCoverArt(track)

            val match = if (hasBadTags || stillNeedsNetworkCoverArt) client.findBestMatch(track) else null
            val networkCoverArtUrl = if (stillNeedsNetworkCoverArt) {
                match?.releaseMbid?.let { coverArtClient.frontCoverUrl(it) }
            } else {
                null
            }
            val coverArtUrl = localCoverArtUrl ?: networkCoverArtUrl

            dao.insert(
                TrackMetadataOverrideEntity(
                    trackId = track.id,
                    title = if (hasBadTags) match?.title else null,
                    artist = if (hasBadTags) match?.artist else null,
                    album = if (hasBadTags) match?.album else null,
                    albumArtUri = coverArtUrl,
                    matchedAtMs = System.currentTimeMillis(),
                ),
            )

            val titleChanged = hasBadTags && match != null
            if (titleChanged || coverArtUrl != null) {
                emit(
                    track.copy(
                        title = if (titleChanged) match!!.title else track.title,
                        artist = if (titleChanged) match!!.artist ?: track.artist else track.artist,
                        album = if (titleChanged) match!!.album ?: track.album else track.album,
                        albumArtUri = coverArtUrl ?: track.albumArtUri,
                    ),
                )
            }
        }
    }
}
