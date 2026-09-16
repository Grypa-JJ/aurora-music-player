package com.aurora.player.domain.model

/**
 * Playlista budowana ręcznie przez użytkownika — patrz DESIGN.md Etap 22.
 * [trackIds] w kolejności odtwarzania; rozwiązywane na [Track] dopiero w warstwie UI
 * (join z pełną biblioteką lokalną+chmurową), tak samo jak planowana nakładka wzbogacania z Etapu 11 —
 * playlisty nie trzymają własnej kopii metadanych utworu, tylko referencję po id.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val trackIds: List<Long>,
    val createdAtMs: Long,
)
