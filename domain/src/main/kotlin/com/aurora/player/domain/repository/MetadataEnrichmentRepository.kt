package com.aurora.player.domain.repository

import com.aurora.player.domain.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Naprawa błędnych/brakujących tytułów i wykonawców przez dopasowanie w MusicBrainz — DESIGN.md
 * Etap 25. Emituje utwory PO KOLEI, w miarę jak wzbogacanie się kończy (nie czeka na całą
 * bibliotekę na raz) — MusicBrainz limituje anonimowe zapytania do ~1/s, więc dla większej liczby
 * "podejrzanych" utworów to naturalnie rozciąga się w czasie zamiast blokować UI.
 */
interface MetadataEnrichmentRepository {
    fun enrichLibrary(tracks: List<Track>): Flow<Track>
}
