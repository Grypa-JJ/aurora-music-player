package com.aurora.player.projectm

import android.content.Context

/**
 * Lokalna, trwała lista presetów zablokowanych przez użytkownika ("nie pokazuj mi tego więcej") —
 * Etap 19/20 (DESIGN.md): zgłoszenie "presety nie są przypisane odpowiednio do danej kategorii".
 * Ręczna rekuracja całej paczki (581 plików `.milk` od zewnętrznego kuratora) nie skaluje się —
 * ten mechanizm pozwala użytkownikowi (albo nam, przy weryfikacji) trwale wykluczyć konkretny,
 * źle dopasowany wygląd w miejscu, gdzie faktycznie się go zobaczy, zamiast zgadywać z nazw plików.
 *
 * Klucz to sama NAZWA pliku (nie pełna ścieżka) — ścieżka absolutna jest per-instalacja
 * (`context.filesDir`), więc zmieniłaby się po reinstalacji/aktualizacji presetów, a nazwa pliku
 * zostaje stabilna.
 */
object PresetBlocklistStore {
    private const val PREFS_NAME = "projectm_preset_blocklist"
    private const val KEY_BLOCKED = "blocked_filenames"

    fun getBlockedFileNames(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BLOCKED, emptySet()).orEmpty()
    }

    fun block(context: Context, fileName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_BLOCKED, emptySet()).orEmpty()
        prefs.edit().putStringSet(KEY_BLOCKED, current + fileName).apply()
    }
}
