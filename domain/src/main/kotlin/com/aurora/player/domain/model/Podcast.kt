package com.aurora.player.domain.model

/** Subskrybowany podcast — DESIGN.md Etap 32 (podcasty). */
data class Podcast(
    val feedUrl: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val description: String,
)

/** Jeden odcinek, parsowany na żywo z RSS przy wejściu w podcast (nie cache'owany w Room). */
data class PodcastEpisode(
    val guid: String,
    val feedUrl: String,
    val title: String,
    val audioUrl: String,
    val durationMs: Long,
    val publishedAtMs: Long,
    val description: String,
)

/** Źródło wyniku wyszukiwania katalogu — dwa niezależne katalogi, patrz [com.aurora.player.domain.repository.PodcastCatalogRepository]. */
enum class PodcastSearchSource { ITUNES, PODCAST_INDEX }

data class PodcastSearchResult(
    val feedUrl: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val source: PodcastSearchSource,
)
