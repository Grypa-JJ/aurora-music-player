package com.aurora.player.domain.repository

import com.aurora.player.domain.model.TranslationResult

/**
 * Tłumaczenie tekstu (ML Kit Translate, model offline pobierany na urządzeniu) — DESIGN.md
 * Etap 54. `targetLanguageCode`/`sourceLanguageCode` to kody BCP-47 (np. "pl", "en").
 */
interface TranslationRepository {
    suspend fun translate(text: String, sourceLanguageCode: String, targetLanguageCode: String): TranslationResult
}
