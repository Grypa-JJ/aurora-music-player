package com.aurora.player.domain.usecase.genius

import com.aurora.player.domain.model.Track
import kotlin.math.abs
import kotlin.math.exp

/**
 * Ważone punktowanie podobieństwa utworów — patrz DESIGN.md sekcja 5.2. W pełni on-device,
 * deterministyczne, bez ML — łatwe do wytłumaczenia i strojenia wag na podstawie skip-rate.
 * Etap 3 (kolejność implementacji w DESIGN.md 5.6, kroki 1-2): gatunek + artysta + rok + tekst +
 * affinity + świeżość. Cooccurrence i kontekst pory dnia świadomie odłożone do etapu 4 —
 * wymagają najpierw zebranej historii odtwarzania z realnego użytkowania.
 */
object GeniusScoring {
    private const val WEIGHT_GENRE = 0.30f
    private const val WEIGHT_ARTIST = 0.25f
    private const val WEIGHT_YEAR = 0.15f
    private const val WEIGHT_TEXT = 0.10f
    private const val WEIGHT_AFFINITY = 0.15f
    private const val WEIGHT_RECENCY = 0.05f

    /** Neutralny start dla utworów bez jeszcze zebranej historii odtworzeń. */
    const val DEFAULT_AFFINITY = 0.3f

    fun score(seed: Track, candidate: Track, affinityScore: Float, nowMs: Long): Float =
        WEIGHT_GENRE * genreMatch(seed.genre, candidate.genre) +
            WEIGHT_ARTIST * artistMatch(seed.artist, candidate.artist) +
            WEIGHT_YEAR * yearProximity(seed.year, candidate.year) +
            WEIGHT_TEXT * textSimilarity(seed.title, candidate.title) +
            WEIGHT_AFFINITY * affinityScore.coerceIn(0f, 1f) +
            WEIGHT_RECENCY * recencyBoost(candidate.dateAddedMs, nowMs)

    private fun genreMatch(a: String?, b: String?): Float {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return 0f
        return if (a.equals(b, ignoreCase = true)) 1f else 0f
    }

    private fun artistMatch(a: String, b: String): Float =
        if (a.isNotBlank() && a.equals(b, ignoreCase = true)) 1f else 0f

    private fun yearProximity(a: Int?, b: Int?): Float {
        if (a == null || b == null) return 0f
        return 1f / (1f + abs(a - b) / 5f)
    }

    private fun textSimilarity(a: String, b: String): Float {
        val tokensA = tokenize(a)
        val tokensB = tokenize(b)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return if (union == 0) 0f else intersection.toFloat() / union
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length > 2 }
            .toSet()

    /** Wykładniczo wygasający boost dla niedawno dodanych utworów (skala: ~30 dni). */
    private fun recencyBoost(dateAddedMs: Long, nowMs: Long): Float {
        if (dateAddedMs <= 0L) return 0f
        val ageDays = (nowMs - dateAddedMs) / 86_400_000f
        return exp(-ageDays / 30f).coerceIn(0f, 1f)
    }
}
