package com.aurora.player.domain.model

/**
 * Skąd pochodzi utwór — patrz DESIGN.md Etap 12/22 (biblioteka z urządzenia + Google Drive +
 * NAS/WebDAV). `CLOUD` zostaje jako nazwa dla Google Drive (zmiana nazwy istniejącej wartości
 * enuma złamałaby dopasowania `when` w kodzie bez żadnej korzyści) — `WEBDAV` to nowe źródło.
 */
enum class TrackSource {
    LOCAL,
    CLOUD,
    WEBDAV,
}
