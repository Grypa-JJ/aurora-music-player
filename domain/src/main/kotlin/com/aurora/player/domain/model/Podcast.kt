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
    /** `<podcast:transcript>` (namespace Podcasting 2.0) — `null` gdy feed go nie publikuje
     *  (większość dziś nie). [transcriptType] to MIME z atrybutu `type` (np. `text/plain`,
     *  `text/vtt`, `application/srt`) — decyduje, jak [transcriptUrl] sparsować. */
    val transcriptUrl: String? = null,
    val transcriptType: String? = null,
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
