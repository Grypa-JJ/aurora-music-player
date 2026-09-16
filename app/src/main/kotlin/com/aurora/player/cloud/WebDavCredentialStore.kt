package com.aurora.player.cloud

import android.content.Context
import androidx.core.content.edit

/**
 * Dane logowania do serwera NAS/WebDAV (Etap 12/22, DESIGN.md) — trwałe w SharedPreferences,
 * żeby nie trzeba było wpisywać ich ponownie po każdym restarcie appki (tak samo jak sesja
 * Google Drive przetrwa restart przez `AuthorizationClient`).
 *
 * Świadomie BEZ szyfrowania (zwykłe, nie `EncryptedSharedPreferences`) — projekt nigdzie indziej
 * nie wprowadził jeszcze Android Keystore/szyfrowania lokalnego magazynu (np. token Google Drive
 * w pamięci procesu, nie na dysku, więc nie jest to bezpośrednio porównywalne), a dodanie go
 * TERAZ tylko dla WebDAV byłoby niespójne z resztą appki. Do rozważenia jako osobne ulepszenie
 * bezpieczeństwa całej appki, nie punktowa łatka tego jednego źródła.
 */
data class WebDavCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
)

object WebDavCredentialStore {
    private const val PREFS_NAME = "webdav_credentials"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"

    fun get(context: Context): WebDavCredentials? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return WebDavCredentials(serverUrl, username, password)
    }

    fun save(context: Context, credentials: WebDavCredentials) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_SERVER_URL, credentials.serverUrl)
            putString(KEY_USERNAME, credentials.username)
            putString(KEY_PASSWORD, credentials.password)
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }
}
