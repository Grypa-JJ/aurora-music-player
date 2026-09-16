package com.aurora.player.domain.util

import com.aurora.player.domain.model.Track

/**
 * Wykrywa utwory z podejrzanymi tagami (puste/placeholder tytuł lub wykonawca) — kandydatów do
 * wzbogacenia przez MusicBrainz (DESIGN.md Etap 25). Świadomie konserwatywne: fałszywy negatyw
 * (nie złapany zły tag) jest tańszy niż fałszywy pozytyw (appka nadpisuje poprawny, tylko
 * nietypowo nazwany utwór).
 */
object TrackMetadataHeuristics {
    private val PLACEHOLDER_TITLE = Regex("""(?i)^(track|audio|untitled|unknown)[\s_-]*\d{0,3}$""")
    private val PLACEHOLDER_ARTIST = Regex("""(?i)^(unknown( artist)?|<unknown>)$""")

    fun looksIncomplete(track: Track): Boolean {
        val title = track.title.trim()
        val artist = track.artist.trim()
        val badTitle = title.isBlank() || title == "Nieznany utwór" || PLACEHOLDER_TITLE.matches(title)
        val badArtist = artist.isBlank() || artist == "Nieznany wykonawca" || PLACEHOLDER_ARTIST.matches(artist)
        return badTitle || badArtist
    }

    /** Osobne kryterium od [looksIncomplete] — DESIGN.md Etap 28: brak okładki nie mówi nic o
     *  jakości tytułu/wykonawcy, więc kandyduje do wzbogacenia niezależnie od tamtej heurystyki. */
    fun needsCoverArt(track: Track): Boolean = track.albumArtUri == null
}
