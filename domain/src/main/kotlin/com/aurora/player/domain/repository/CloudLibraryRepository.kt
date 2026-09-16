package com.aurora.player.domain.repository

import com.aurora.player.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Biblioteka w chmurze (Google Drive) obok biblioteki lokalnej z MediaStore — patrz DESIGN.md,
 * sekcja "Chmura". Logowanie samo w sobie (Activity Result/Intent) zostaje w warstwie `app`,
 * bo `domain` jest czystym Kotlinem bez zależności do Androida — stąd brak tu np. `signIn()`.
 */
interface CloudLibraryRepository {
    val isSignedIn: StateFlow<Boolean>
    val accountEmail: StateFlow<String?>

    /**
     * Czytelny opis ostatniego błędu logowania/autoryzacji (Etap 18/22 — zgłoszenie "kliknięcie
     * w konto nic nie robi", flow dotąd połykał każdy błąd w ciszy). `null` = brak błędu do
     * pokazania. UI czyści to przez [clearLastError] po wyświetleniu (np. Snackbar).
     */
    val lastError: StateFlow<String?>
    fun clearLastError()

    /** Utwory audio ze skonfigurowanego konta Google Drive; pusta lista gdy niezalogowany. */
    suspend fun refreshCloudTracks(): List<Track>

    fun signOut()

    /**
     * Blokujące pobranie świeżego tokenu OAuth do nagłówka `Authorization` przy odtwarzaniu —
     * wołane WYŁĄCZNIE z wątku ładowania Media3 (nigdy z UI/main thread), patrz
     * `GoogleDriveDataSourceFactory` w module `app`. `null` gdy niezalogowany lub błąd sieci.
     */
    fun currentAccessTokenBlocking(): String?
}
