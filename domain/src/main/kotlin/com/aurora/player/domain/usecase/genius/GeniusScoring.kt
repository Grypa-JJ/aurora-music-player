package com.aurora.player.domain.usecase.genius

import com.aurora.player.domain.model.Track
import kotlin.math.abs
import kotlin.math.exp

/**
 * Ważone punktowanie podobieństwa utworów — patrz DESIGN.md sekcja 5.2. W pełni on-device,
 * deterministyczne, bez ML — łatwe do wytłumaczenia i strojenia wag na podstawie skip-rate.
 * Etap 4: pełna formuła z DESIGN.md włącznie z cooccurrence (co-play) i kontekstem pory dnia —
 * oba sygnały wymagały najpierw zebranej historii odtwarzania (etap 3), dlatego doszły później.
 */
object GeniusScoring {
    private const val WEIGHT_GENRE = 0.25f
    private const val WEIGHT_ARTIST = 0.20f
    private const val WEIGHT_YEAR = 0.10f
    private const val WEIGHT_TEXT = 0.10f
    private const val WEIGHT_COOCCURRENCE = 0.15f
    private const val WEIGHT_AFFINITY = 0.10f
    private const val WEIGHT_CONTEXT = 0.05f
    private const val WEIGHT_RECENCY = 0.05f

    // Powyższe wagi sumują się do 1.00 (pozytywne sygnały podobieństwa). Kara za niedawny skip
    // (DESIGN.md Etap 27) jest CELOWO poza tym budżetem — to nie "jeszcze jeden pozytywny sygnał
    // do zrównoważenia", tylko osobna, odejmowana korekta za jawny negatywny feedback usera.
    private const val WEIGHT_SKIP_PENALTY = 0.20f

    /** Neutralny start dla utworów bez jeszcze zebranej historii odtworzeń. */
    const val DEFAULT_AFFINITY = 0.3f

    fun score(
        seed: Track,
        candidate: Track,
        affinityScore: Float,
        cooccurrenceScore: Float,
        contextScore: Float,
        nowMs: Long,
        skipPenalty: Float = 0f,
    ): Float =
        WEIGHT_GENRE * genreMatch(seed.genre, candidate.genre) +
            WEIGHT_ARTIST * artistMatch(seed.artist, candidate.artist) +
            WEIGHT_YEAR * yearProximity(seed.year, candidate.year) +
            WEIGHT_TEXT * textSimilarity(seed.title, candidate.title) +
            WEIGHT_COOCCURRENCE * cooccurrenceScore.coerceIn(0f, 1f) +
            WEIGHT_AFFINITY * affinityScore.coerceIn(0f, 1f) +
            WEIGHT_CONTEXT * contextScore.coerceIn(0f, 1f) +
            WEIGHT_RECENCY * recencyBoost(candidate.dateAddedMs, nowMs) -
            WEIGHT_SKIP_PENALTY * skipPenalty.coerceIn(0f, 1f)

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
