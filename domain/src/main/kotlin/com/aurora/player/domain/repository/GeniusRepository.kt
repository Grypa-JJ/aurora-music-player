package com.aurora.player.domain.repository

import com.aurora.player.domain.model.GeniusMix
import com.aurora.player.domain.model.Track

/**
 * "Genius" — lokalne rekomendacje muzyczne bez sieci/chmury, patrz DESIGN.md sekcja 5.
 *
 * [candidateTracks] jest podawane przez wywołującego (nie dociągane wewnętrznie z
 * [com.aurora.player.domain.repository.TrackRepository], jak przed DESIGN.md Etap 40) — inaczej
 * Genius widziałby WYŁĄCZNIE zeskanowane pliki z urządzenia, gubiąc Google Drive/NAS-WebDAV/
 * pobrane na stałe z Archiwum, które żyją tylko w zagregowanej liście po stronie wywołującego
 * (`LibraryViewModel.uiState.allTracks` w appce, `TrackRepository.getAllTracks()` w Android Auto,
 * które świadomie zostaje lokalne-only, bo cała reszta jego drzewa przeglądania też jest).
 */
interface GeniusRepository {
    /** Buduje "Instant Mix" z utworu-ziarna (bez samego seeda w wyniku) — DESIGN.md sekcja 5.3. */
    suspend fun generateInstantMix(seedTrackId: Long, candidateTracks: List<Track>, length: Int = 30): List<Track>

    /** Gotowe playlisty z klastrowania, bez wskazywania utworu-ziarna — DESIGN.md sekcja 5.5. */
    suspend fun generateGeniusMixes(
        candidateTracks: List<Track>,
        maxMixes: Int = 6,
        tracksPerMix: Int = 25,
    ): List<GeniusMix>
}
