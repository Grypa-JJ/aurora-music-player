package com.aurora.player.domain.usecase.genius

import com.aurora.player.domain.model.Track

/**
 * Buduje finalną playlistę z posortowanych kandydatów, egzekwując limity dywersyfikacji —
 * patrz DESIGN.md sekcja 5.3, krok 3. Bez tego Instant Mix potrafiłby wylądować jako
 * "10 utworów tego samego artysty z rzędu", co jest najbardziej odczuwalną jakościowo wadą.
 */
object GeniusDiversifier {
    private const val MAX_SAME_ARTIST_STREAK = 2
    private const val MAX_ALBUM_SHARE = 0.3f

    fun diversify(scoredDescending: List<Track>, targetLength: Int): List<Track> {
        if (targetLength <= 0) return emptyList()

        val result = mutableListOf<Track>()
        val albumCounts = HashMap<String, Int>()
        var lastArtist: String? = null
        var artistStreak = 0
        val maxPerAlbum = (targetLength * MAX_ALBUM_SHARE).toInt().coerceAtLeast(1)

        for (track in scoredDescending) {
            if (result.size >= targetLength) break
            val wouldExtendArtistStreak = track.artist == lastArtist && artistStreak >= MAX_SAME_ARTIST_STREAK
            val albumIsFull = (albumCounts[track.album] ?: 0) >= maxPerAlbum
            if (wouldExtendArtistStreak || albumIsFull) continue

            result += track
            albumCounts[track.album] = (albumCounts[track.album] ?: 0) + 1
            if (track.artist == lastArtist) artistStreak++ else { artistStreak = 1; lastArtist = track.artist }
        }

        // Mała biblioteka może nie mieć dość "legalnych" kandydatów — dopełnij resztą wg score,
        // żeby Instant Mix nie był krótszy niż oczekiwano tylko przez reguły dywersyfikacji.
        if (result.size < targetLength) {
            for (track in scoredDescending) {
                if (result.size >= targetLength) break
                if (track !in result) result += track
            }
        }

        return result
    }
}
