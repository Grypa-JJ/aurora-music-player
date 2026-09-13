package com.aurora.player.domain.usecase.genius

import com.aurora.player.domain.model.Track
import kotlin.random.Random

/**
 * Proste k-means do "Genius Mixes" bez seeda — patrz DESIGN.md sekcja 5.5. Wektor cechy:
 * one-hot top-K gatunków biblioteki (+ "inne"), znormalizowany rok, affinity — świadomie bez
 * one-hot artystów (dodatkowy wymiar niewiele wnosi przy tej skali i komplikuje dobór K).
 * Deterministyczne (stały seed) — te same klastry przy każdym odświeżeniu ekranu Genius,
 * dopóki biblioteka lub historia odsłuchań się nie zmieni.
 */
object GeniusClustering {
    private const val ITERATIONS = 10
    private const val RANDOM_SEED = 42L

    fun cluster(tracks: List<Track>, affinityByTrackId: Map<Long, Float>, k: Int): List<List<Track>> {
        if (k <= 0 || tracks.size < k) return if (tracks.isEmpty()) emptyList() else listOf(tracks)

        val topGenres = tracks
            .mapNotNull { it.genre?.trim()?.lowercase() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(k)
            .map { it.key }

        val years = tracks.mapNotNull { it.year }
        val minYear = years.minOrNull() ?: 1900
        val maxYear = years.maxOrNull() ?: 2030
        val yearRange = (maxYear - minYear).coerceAtLeast(1)

        fun vectorize(track: Track): FloatArray {
            val genreVector = FloatArray(topGenres.size + 1)
            val genre = track.genre?.trim()?.lowercase().orEmpty()
            val genreIndex = topGenres.indexOf(genre)
            if (genreIndex >= 0) genreVector[genreIndex] = 1f else genreVector[topGenres.size] = 1f

            val yearNorm = if (track.year != null) {
                (track.year - minYear).toFloat() / yearRange
            } else {
                0.5f
            }
            val affinity = affinityByTrackId[track.id] ?: GeniusScoring.DEFAULT_AFFINITY

            return genreVector + floatArrayOf(yearNorm, affinity)
        }

        val vectors = tracks.map(::vectorize)
        val dimensions = vectors.first().size
        val random = Random(RANDOM_SEED)

        var centroids = vectors.shuffled(random).take(k).toMutableList()
        var assignments = IntArray(tracks.size)

        repeat(ITERATIONS) {
            for (i in vectors.indices) {
                assignments[i] = centroids.indices.minByOrNull { c -> squaredDistance(vectors[i], centroids[c]) } ?: 0
            }

            val sums = Array(k) { FloatArray(dimensions) }
            val counts = IntArray(k)
            for (i in vectors.indices) {
                val cluster = assignments[i]
                counts[cluster]++
                for (d in 0 until dimensions) sums[cluster][d] += vectors[i][d]
            }

            centroids = (0 until k).map { c ->
                if (counts[c] > 0) FloatArray(dimensions) { d -> sums[c][d] / counts[c] } else centroids[c]
            }.toMutableList()
        }

        return (0 until k)
            .map { cluster -> tracks.filterIndexed { i, _ -> assignments[i] == cluster } }
            .filter { it.isNotEmpty() }
    }

    fun nameFor(cluster: List<Track>, index: Int): String {
        val dominantGenre = cluster
            .mapNotNull { it.genre?.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        return if (dominantGenre != null) "Genius Mix: $dominantGenre" else "Genius Mix ${index + 1}"
    }

    private fun squaredDistance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sum
    }
}
