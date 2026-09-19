package com.aurora.player.translation

import android.util.Log
import com.aurora.player.domain.model.TranslationResult
import com.aurora.player.domain.repository.TranslationRepository
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Tłumaczenie transkrypcji podkastów (ML Kit Translate) — DESIGN.md Etap 54, zgłoszenie:
 * "text to speech żeby można było tłumaczyć z ang na polski". Świadomie ML Kit zamiast chmurowego
 * API (Google Translate/DeepL): model pobierany RAZ na urządzenie, tłumaczenie potem całkowicie
 * offline i bez kosztu za żądanie/klucza API — user nie musi zakładać konta w żadnym third-party
 * serwisie ani martwić się rachunkiem za każde odtworzenie transkrypcji.
 *
 * Bez `kotlinx-coroutines-play-services` (nowa zależność tylko po to, żeby zawinąć `Task` w
 * `await()`) — ręczny `suspendCancellableCoroutine` robi to samo dla tych 2 wywołań.
 */
@Singleton
class MlKitTranslationRepositoryImpl @Inject constructor() : TranslationRepository {

    // Zgłoszenie: "tłumaczenie laguje" — `Task.addOnSuccessListener(listener)` BEZ jawnego
    // Executora domyślnie odpala listener na wątku GŁÓWNYM. Przy długim odcinku (setki fragmentów
    // po ~800 znaków) to setki wznowień korutyny NA GŁÓWNYM wątku pod rząd — stąd zacinanie się UI
    // przez cały czas tłumaczenia, nie tylko na starcie. Dwa niezależne poziomy fixu: (1) `executor`
    // przekazany do KAŻDEGO listenera ML Kit, żeby same callbacki nie lądowały na głównym wątku;
    // (2) cała funkcja w `withContext(Dispatchers.Default)`, żeby kontekst korutyny (czyli wątek,
    // na który realnie wraca wykonanie po każdym `awaitTranslate`) też był tłem, niezależnie od
    // tego, skąd woła `onTranslateTranscript` w ViewModelu.
    private val executor = Dispatchers.Default.asExecutor()

    override suspend fun translate(text: String, sourceLanguageCode: String, targetLanguageCode: String): TranslationResult =
        withContext(Dispatchers.Default) {
            val sourceTag = TranslateLanguage.fromLanguageTag(sourceLanguageCode) ?: return@withContext TranslationResult.Failed
            val targetTag = TranslateLanguage.fromLanguageTag(targetLanguageCode) ?: return@withContext TranslationResult.Failed
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceTag)
                .setTargetLanguage(targetTag)
                .build()
            val translator = Translation.getClient(options)
            try {
                val downloaded = awaitDownloadModel(translator)
                if (!downloaded) return@withContext TranslationResult.Failed

                // Teksty transkrypcji potrafią mieć tysiące słów — tłumaczymy akapitami po ~800
                // znaków (cięcie na granicy spacji, nie w środku słowa), żeby uniknąć jednego
                // gigantycznego wywołania i pokazać wynik stopniowo, gdyby kiedyś dołożyć streaming UI.
                val chunks = chunkText(text, maxChunkChars = 800)
                val translatedChunks = chunks.map { chunk ->
                    awaitTranslate(translator, chunk) ?: return@withContext TranslationResult.Failed
                }
                TranslationResult.Translated(translatedChunks.joinToString(" "))
            } finally {
                translator.close()
            }
        }

    private suspend fun awaitDownloadModel(translator: com.google.mlkit.nl.translate.Translator): Boolean =
        suspendCancellableCoroutine { continuation ->
            val conditions = com.google.mlkit.common.model.DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(executor) { continuation.resume(true) }
                .addOnFailureListener(executor) { e ->
                    Log.e(TAG, "downloadModelIfNeeded(): nie udało się pobrać modelu", e)
                    continuation.resume(false)
                }
        }

    private suspend fun awaitTranslate(translator: com.google.mlkit.nl.translate.Translator, text: String): String? =
        suspendCancellableCoroutine { continuation ->
            translator.translate(text)
                .addOnSuccessListener(executor) { result -> continuation.resume(result) }
                .addOnFailureListener(executor) { e ->
                    Log.e(TAG, "translate(): błąd tłumaczenia fragmentu", e)
                    continuation.resume(null)
                }
        }

    private fun chunkText(text: String, maxChunkChars: Int): List<String> {
        if (text.length <= maxChunkChars) return listOf(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + maxChunkChars).coerceAtMost(text.length)
            if (end < text.length) {
                val lastSpace = text.lastIndexOf(' ', end)
                if (lastSpace > start) end = lastSpace
            }
            chunks += text.substring(start, end).trim()
            start = end
        }
        return chunks.filter { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "MlKitTranslationRepo"
    }
}
