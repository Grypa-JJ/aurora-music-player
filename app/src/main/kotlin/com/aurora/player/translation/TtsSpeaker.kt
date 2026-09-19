package com.aurora.player.translation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Czyta przetłumaczony tekst na głos — silnik TTS wbudowany w system (Google Text-to-Speech na
 * Androidzie, darmowy, offline po pierwszym pobraniu głosu przez system), zero nowej zależności —
 * DESIGN.md Etap 54, zgłoszenie: "syntezator mowy jako kolejny krok po tłumaczeniu".
 *
 * Świadomie BEZ synchronizacji z oryginalnym audio (ducking/tempo per zdanie, patrz dyskusja w
 * zgłoszeniu) — to osobny, dużo większy temat. Appka pauzuje odtwarzanie na czas czytania
 * (patrz `LibraryViewModel.onSpeakTranslatedTranscript`) zamiast nakładać dwa dźwięki naraz.
 */
@Singleton
class TtsSpeaker @Inject constructor(@ApplicationContext context: Context) {
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private var isReady = false
    private val tts = TextToSpeech(context) { status -> isReady = status == TextToSpeech.SUCCESS }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Wymagane przez UtteranceProgressListener (starszy callback bez kodu błędu)")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }
        })
    }

    /** `false` = brak polskiego głosu na urządzeniu (rzadkie, ale możliwe na bardzo starych ROM-ach). */
    fun speak(text: String, languageTag: String = "pl"): Boolean {
        if (!isReady) return false
        val result = tts.setLanguage(Locale.forLanguageTag(languageTag))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return false
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        return true
    }

    fun stop() {
        tts.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts.shutdown()
    }
}
