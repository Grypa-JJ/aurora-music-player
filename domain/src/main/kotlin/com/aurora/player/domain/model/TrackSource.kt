package com.aurora.player.domain.model

/**
 * Skąd pochodzi utwór — patrz DESIGN.md Etap 12/22/24 (biblioteka z urządzenia + Google Drive +
 * NAS/WebDAV + radio + podkasty). `CLOUD` zostaje jako nazwa dla Google Drive (zmiana nazwy
 * istniejącej wartości enuma złamałaby dopasowania `when` w kodzie bez żadnej korzyści) —
 * `WEBDAV`/`RADIO`/`PODCAST`/`INDEPENDENT`/`ARCHIVE`/`AUDIOBOOK` to nowe źródła. Radio/Podcast/
 * Independent/Archive/Audiobook to syntetyczne [Track] (bez realnego pliku w bibliotece) budowane
 * tylko na potrzeby odtworzenia przez współdzielony
 * [com.aurora.player.domain.repository.PlayerRepository] — patrz mappery w pakietach
 * `radio`/`podcast`/`independent`/`archive`/`audiobook`.
 */
enum class TrackSource {
    LOCAL,
    CLOUD,
    WEBDAV,
    RADIO,
    PODCAST,
    INDEPENDENT,
    ARCHIVE,
    AUDIOBOOK,
}
