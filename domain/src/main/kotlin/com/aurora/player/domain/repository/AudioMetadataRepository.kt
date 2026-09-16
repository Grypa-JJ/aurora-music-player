package com.aurora.player.domain.repository

import com.aurora.player.domain.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Dociąga realne parametry audio (codec/bitrate/sample rate/bit depth) dla utworów, które ich
 * jeszcze nie mają — DESIGN.md Etap 29, prerekwizyt pod przyszły Audio Lab (Etap 20h). Lokalny
 * odczyt pliku (`MediaExtractor`), nie sieć — świadomie wolniejszy, drugoplanowy proces, nie
 * blokuje zwykłego ładowania biblioteki.
 */
interface AudioMetadataRepository {
    fun extractMissing(tracks: List<Track>): Flow<Track>
}
