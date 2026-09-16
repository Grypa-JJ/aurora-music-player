package com.aurora.player.podcast

import android.content.Context
import androidx.core.content.edit

/**
 * Klucz Podcast Index — WŁASNY klucz usera (darmowy, natychmiastowy na podcastindex.org), NIGDY
 * jeden wspólny klucz appki — patrz DESIGN.md Etap 32, decyzja usera po dyskusji o modelu
 * biznesowym (wspólny klucz dzieliłby limit requestów między wszystkich userów i łamałby
 * regulamin API). Ten sam wzorzec trwałości co WebDavCredentialStore.
 */
data class PodcastIndexCredentials(val apiKey: String, val apiSecret: String)

object PodcastIndexCredentialStore {
    private const val PREFS_NAME = "podcast_index_credentials"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_API_SECRET = "api_secret"

    fun get(context: Context): PodcastIndexCredentials? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(KEY_API_KEY, null) ?: return null
        val apiSecret = prefs.getString(KEY_API_SECRET, null) ?: return null
        return PodcastIndexCredentials(apiKey, apiSecret)
    }

    fun save(context: Context, credentials: PodcastIndexCredentials) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_API_KEY, credentials.apiKey)
            putString(KEY_API_SECRET, credentials.apiSecret)
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }
}
