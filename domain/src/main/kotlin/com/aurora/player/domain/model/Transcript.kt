package com.aurora.player.domain.model

/** Wynik pobrania transkrypcji odcinka podkastu — DESIGN.md Etap 54 (`<podcast:transcript>`). */
sealed interface TranscriptResult {
    data class Loaded(val text: String) : TranscriptResult
    data object NotAvailable : TranscriptResult
}

/** Wynik tłumaczenia (ML Kit, offline) — [NotDownloaded] gdy model języka jeszcze nie ściągnięty. */
sealed interface TranslationResult {
    data class Translated(val text: String) : TranslationResult
    data object Failed : TranslationResult
}
