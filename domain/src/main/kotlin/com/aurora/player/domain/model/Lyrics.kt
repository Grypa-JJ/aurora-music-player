package com.aurora.player.domain.model

/** Jedna linia zsynchronizowanego tekstu — [timestampMs] liczony od początku utworu. */
data class LyricsLine(val timestampMs: Long, val text: String)

/** Wynik wyszukania tekstu dla utworu — DESIGN.md Etap 24 (LRCLIB, offline-first cache). */
sealed interface LyricsResult {
    data class Synced(val lines: List<LyricsLine>) : LyricsResult
    data class Plain(val text: String) : LyricsResult
    data object NotFound : LyricsResult
}
