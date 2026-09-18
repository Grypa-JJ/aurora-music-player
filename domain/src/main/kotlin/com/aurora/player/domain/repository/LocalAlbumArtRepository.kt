package com.aurora.player.domain.repository

import com.aurora.player.domain.model.Track

/**
 * Dociąga okładkę albumu dla JEDNEGO lokalnego utworu — DESIGN.md Etap 42/44. Zastępuje stary,
 * udokumentowanie zawodny na Androidzie 10+ `content://media/external/audio/albumart/{id}`
 * (system UI ma do niego własną, odporniejszą ścieżkę, której Coil w appce nie ma —
 * `ContentResolver.loadThumbnail()` to oficjalny następca). Świadomie per-utwór, nie
 * per-biblioteka — patrz komentarz w [com.aurora.player.domain.repository.MetadataEnrichmentRepository]
 * o Etapie 44: to WOŁAJĄCY (jeden, wspólny potok wzbogacania) decyduje o kolejności/limicie
 * czasu wobec sieciowego fallbacku, nie ta metoda.
 */
interface LocalAlbumArtRepository {
    suspend fun resolveOne(track: Track): String?
}
