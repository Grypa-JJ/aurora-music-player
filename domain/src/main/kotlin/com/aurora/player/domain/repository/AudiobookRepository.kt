package com.aurora.player.domain.repository

import com.aurora.player.domain.model.Audiobook
import com.aurora.player.domain.model.AudiobookChapter
import com.aurora.player.domain.model.AudiobookSearchResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Audiobooki (LibriVox, domena publiczna) — jedno źródło, więc wyszukiwanie katalogu i biblioteka
 * usera żyją w jednym interfejsie (inaczej niż Podcasty, gdzie dwa niezależne katalogi uzasadniały
 * osobny [PodcastCatalogRepository]).
 */
interface AudiobookRepository {
    val library: StateFlow<List<Audiobook>>

    suspend fun search(query: String, limit: Int = 40): List<AudiobookSearchResult>

    /**
     * Propozycje wg języka + popularności — DESIGN.md Etap 37. LibriVox samo nie ma pojęcia
     * popularności, ale te same nagrania są też w kolekcji `librivoxaudio` na Internet Archive,
     * która ma realną liczbę pobrań ORAZ kod języka; `call_number` w metadanych IA to dokładnie
     * numeryczne id książki z API LibriVox (zweryfikowane), więc wynik da się od razu dodać do
     * biblioteki bez dodatkowego zapytania. Gdy [languageIso3] nie ma wyników, wywołujący powinien
     * spaść na `"eng"` — nigdy pusty ekran.
     */
    suspend fun trending(languageIso3: String, limit: Int = 30): List<AudiobookSearchResult>

    /** Dociąga metadane książki z LibriVox po [id], zapisuje w bibliotece. `null` gdy nie znaleziono. */
    suspend fun addToLibrary(id: String): Audiobook?
    suspend fun removeFromLibrary(id: String)

    /** Świeże pobranie listy rozdziałów przy każdym wejściu (LibriVox zwraca je gotowo posortowane). */
    suspend fun fetchChapters(id: String): List<AudiobookChapter>

    suspend fun getPlaybackPosition(trackId: Long): Long
    suspend fun savePlaybackPosition(trackId: Long, positionMs: Long)
}
