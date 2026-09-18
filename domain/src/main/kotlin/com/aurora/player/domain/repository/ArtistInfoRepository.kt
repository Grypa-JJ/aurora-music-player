package com.aurora.player.domain.repository

import com.aurora.player.domain.model.ArtistInfo

/**
 * Biografia/gatunek/grafika wykonawcy z TheAudioDB — DESIGN.md Etap 43. Cache najpierw (Room,
 * także negatywny), sieć tylko gdy trzeba — ten sam wzorzec co [MetadataEnrichmentRepository].
 */
interface ArtistInfoRepository {
    suspend fun getArtistInfo(artistName: String): ArtistInfo?
}
