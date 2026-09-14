package com.aurora.player.projectm

/**
 * Panel ustawień wizualizera (Etap 10 część 2, DESIGN.md) — ŚWIADOMIE tylko parametry mające
 * realny odpowiednik w API projectM ([ProjectMEngine.setBeatSensitivity]/[ProjectMEngine.setHardCut]/
 * `setPresetDuration`) albo w naszym własnym kodzie ([GlowColorSource] — patrz `AmbientGlow`).
 * Z oryginalnej listy z rozmowy ("rozmycie", osobna "czułość wysokich") świadomie pominięte —
 * projectM nie eksponuje takich pokręteł, a udawanie suwaka bez żadnego realnego efektu byłoby
 * nieuczciwe względem użytkownika.
 */
data class ProjectMVisualizerSettings(
    /** "Szybkość animacji" — jak długo pojedynczy preset gra, zanim playlist wybierze kolejny. */
    val presetDurationSeconds: Double = DEFAULT_PRESET_DURATION_SECONDS,
    /** "Czułość na beat" — realny parametr `projectm_set_beat_sensitivity`. */
    val beatSensitivity: Float = DEFAULT_BEAT_SENSITIVITY,
    /** Czy mocne uderzenie basu może wymusić natychmiastową (nie płynną) zmianę presetu. */
    val hardCutEnabled: Boolean = true,
    val hardCutSensitivity: Float = DEFAULT_HARD_CUT_SENSITIVITY,
    val colorSource: GlowColorSource = GlowColorSource.ALBUM_ART,
) {
    companion object {
        const val MIN_PRESET_DURATION_SECONDS = 5.0
        const val MAX_PRESET_DURATION_SECONDS = 60.0
        const val DEFAULT_PRESET_DURATION_SECONDS = 20.0

        const val MIN_SENSITIVITY = 0.1f
        const val MAX_SENSITIVITY = 3f
        // Punkt startowy suwaka wybrany przez nas (bez wywołania te parametry mają swoją własną
        // wartość domyślną wewnątrz projectM, której nie odczytujemy) — nie "prawdziwy default"
        // biblioteki, tylko rozsądny środek skali do pokazania w UI.
        const val DEFAULT_BEAT_SENSITIVITY = 1f
        const val DEFAULT_HARD_CUT_SENSITIVITY = 1f

        /**
         * Punkt startowy tempa zmiany presetów wg gatunku (Etap 10: "adaptacja do gatunku",
         * głębsza niż sam dobór [ProjectMVisualizerMode]) — spokojniejsze gatunki dostają dłuższe,
         * mniej rwące przejścia; energetyczne — szybsze, bardziej dynamiczne. Użytkownik i tak może
         * to nadpisać suwakiem.
         */
        fun defaultPresetDurationForGenre(genre: String?): Double {
            val normalized = genre?.trim()?.lowercase().orEmpty()
            if (normalized.isEmpty()) return DEFAULT_PRESET_DURATION_SECONDS
            val calm = listOf("classical", "klasyczna", "jazz", "acoustic", "akustyczna", "ambient", "chill", "piano")
            val energetic = listOf("electronic", "techno", "edm", "dance", "punk", "metal", "hip-hop", "hip hop")
            return when {
                calm.any { it in normalized } -> 35.0
                energetic.any { it in normalized } -> 12.0
                else -> DEFAULT_PRESET_DURATION_SECONDS
            }
        }
    }
}

/**
 * Skąd bierze kolor [AmbientGlow] (poświata wokół wizualizera, patrz DESIGN.md Etap 10 część 2) —
 * NIE tinguje samego renderu presetu, to niemożliwe bez ingerencji w shadery projectM (presety
 * same decydują o swoich kolorach). Realnie synchronizowane jest tylko otoczenie.
 */
enum class GlowColorSource(val displayName: String) {
    ALBUM_ART("Okładka"),
    MONOCHROME("Monochromatyczne"),
}
