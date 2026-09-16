package com.aurora.player.projectm

import android.content.Context
import java.io.File

/**
 * projectM czyta presety `.milk` zwykłym `std::ifstream` (zweryfikowane w źródle
 * `PresetFileParser.cpp`) — nie potrafi czytać bezpośrednio z `assets/` wewnątrz APK
 * (`AAssetManager` to co innego niż zwykła ścieżka POSIX). Stąd jednorazowa kopia z assets do
 * pamięci wewnętrznej appki przy pierwszym uruchomieniu (lub po aktualizacji paczki presetów).
 */
object PresetInstaller {
    private const val ASSET_DIR = "projectm_presets"
    private const val TARGET_DIR_NAME = "projectm_presets"
    private const val VERSION_MARKER_FILE = ".installed_version"

    // Zwiększ przy każdej zmianie zawartości core/projectm/src/main/assets/projectm_presets/,
    // żeby wymusić re-ekstrakcję na urządzeniach z już zainstalowaną starszą wersją.
    // v2 (Etap 19/20): usunięto 8 presetów "TonyMilkdrop" z Hypnotic — zweryfikowane na żywo
    // (wielokrotnie, na różnych utworach/trybach), że renderują to samo agresywne logo "M" ze
    // słuchawkami zamiast spokojnej, ambientowej treści; zgłoszenie: "presety nie są przypisane
    // odpowiednio do danej kategorii". Patrz DESIGN.md Etap 20 — to częściowa, potwierdzona
    // poprawka, nie pełna rekuracja całej paczki (581 presetów).
    private const val CURRENT_VERSION = 2

    /** Zwraca ścieżkę katalogu z gotowymi do użycia plikami `.milk` na dysku. */
    fun ensureInstalled(context: Context): File {
        val targetDir = File(context.filesDir, TARGET_DIR_NAME)
        val markerFile = File(targetDir, VERSION_MARKER_FILE)
        val installedVersion = markerFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (installedVersion == CURRENT_VERSION) return targetDir

        targetDir.deleteRecursively()
        targetDir.mkdirs()
        copyAssetTree(context, ASSET_DIR, targetDir)
        markerFile.writeText(CURRENT_VERSION.toString())
        return targetDir
    }

    private fun copyAssetTree(context: Context, assetPath: String, targetDir: File) {
        val assetManager = context.assets
        val children = assetManager.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            targetDir.parentFile?.mkdirs()
            assetManager.open(assetPath).use { input ->
                targetDir.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        targetDir.mkdirs()
        for (child in children) {
            copyAssetTree(context, "$assetPath/$child", File(targetDir, child))
        }
    }
}
