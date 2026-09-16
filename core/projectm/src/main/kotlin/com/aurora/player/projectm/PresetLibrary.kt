package com.aurora.player.projectm

import java.io.File

/**
 * Lista plików `.milk` dla danego [ProjectMVisualizerMode] — patrz DESIGN.md Etap 10/16.
 *
 * ŚWIADOMIE zastępuje wcześniejsze poleganie na natywnej bibliotece playlist projectM
 * (`projectm_playlist_add_path`/`play_next`) dla WYBORU presetu: playlist sama trzyma pozycję
 * wewnątrz natywnego stanu, którego nie da się odczytać/przenieść między dwiema OSOBNYMI
 * instancjami `ProjectMSurfaceView` (ramka inline i pełny ekran, patrz DESIGN.md Etap 9 —
 * `movableContentOf` się nie sprawdziło). Zgłoszony bug: przejście ramka->pełny ekran losowało
 * nowy preset zamiast zachować ten, na który patrzył użytkownik. Kotlin, listując pliki sam i
 * trzymając indeks jako zwykły stan Compose (lifted do `NowPlayingScreen`), może tę ciągłość
 * zagwarantować wprost — `ProjectMEngine.loadPresetFile()` (bezpośrednio `projectm_load_preset_file`)
 * ładuje dokładnie wskazany plik, playlist w ogóle nie jest już używany do wyboru presetu.
 */
object PresetLibrary {
    /**
     * @param blockedFileNames nazwy plików (nie pełne ścieżki — patrz [PresetBlocklistStore])
     *   trwale wykluczone przez użytkownika z losowania/cyklu, niezależnie od kategorii.
     */
    fun listPresets(
        presetsRootDir: String,
        mode: ProjectMVisualizerMode,
        blockedFileNames: Set<String> = emptySet(),
    ): List<String> {
        val folders = mode.presetSubfolders?.map { File(presetsRootDir, it) }
            ?: listOf(File(presetsRootDir))
        return folders
            .filter { it.isDirectory }
            .flatMap { folder ->
                folder.walkTopDown()
                    .filter { it.isFile && it.extension.equals("milk", ignoreCase = true) }
                    .filterNot { it.name in blockedFileNames }
                    .map { it.absolutePath }
                    .toList()
            }
            // Stabilna kolejność (nie ufamy kolejności enumeracji systemu plików) — potrzebne,
            // żeby ten sam indeks znaczył ten sam plik przy każdym budowaniu listy.
            .sorted()
    }
}
