package com.aurora.player.projectm

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Tryby wizualizera z Etapu 10 (DESIGN.md) — świadomie NIE trzy osobne silniki renderujące
 * (to byłoby powtórzeniem błędu z Etapów 7-8, które user odrzucił jako niewystarczające), tylko
 * filtrowanie zestawu presetów Milkdrop, które już mamy skurowane per kategoria (patrz podfoldery
 * `core/projectm/src/main/assets/projectm_presets/presets/`, wybór i liczebność opisane
 * w DESIGN.md Etap 9). Nazwy folderów pochodzą od kuratora paczki "Cream of the Crop", nie od nas
 * — mapowanie na Ambient/Spectrum/Particle to nasza interpretacja na podstawie nazw i typowej
 * zawartości tych kategorii, nie gwarancja że każdy pojedynczy preset pasuje idealnie do nastroju.
 */
enum class ProjectMVisualizerMode(
    val displayName: String,
    /** null = cały skurowany zestaw (`presets/`), tak jak dotąd — bez zmian domyślnego zachowania. */
    val presetSubfolders: List<String>?,
    /** Używana przez kompaktowy przycisk trybu (małe okno) — patrz `CompactModeButton` w ProjectMSurface.kt. */
    val icon: ImageVector,
) {
    ALL("Wszystkie", null, Icons.Filled.Apps),
    AMBIENT("Ambient", listOf("Hypnotic", "Drawing"), Icons.Filled.BlurOn),
    SPECTRUM("Spectrum", listOf("Waveform"), Icons.Filled.GraphicEq),
    PARTICLE("Particle", listOf("Particles", "Sparkle", "Supernova"), Icons.Filled.Grain),
    ;

    companion object {
        /**
         * Domyślny tryb wg gatunku utworu (Etap 10: "adaptacja do gatunku") — prosta heurystyka
         * na słowach kluczowych, nie klasyfikator. Użytkownik zawsze może nadpisać ręcznie
         * przełącznikiem trybu — to tylko sensowny punkt startowy, nie sztywna reguła.
         */
        fun defaultForGenre(genre: String?): ProjectMVisualizerMode {
            val normalized = genre?.trim()?.lowercase().orEmpty()
            if (normalized.isEmpty()) return ALL
            return when {
                AMBIENT_KEYWORDS.any { it in normalized } -> AMBIENT
                SPECTRUM_KEYWORDS.any { it in normalized } -> SPECTRUM
                PARTICLE_KEYWORDS.any { it in normalized } -> PARTICLE
                else -> ALL
            }
        }

        private val AMBIENT_KEYWORDS = listOf(
            "jazz", "classical", "klasyczna", "acoustic", "akustyczna", "ambient", "chill",
            "lo-fi", "lofi", "piano",
        )
        private val SPECTRUM_KEYWORDS = listOf(
            "electronic", "elektroniczna", "techno", "house", "edm", "trance", "synth",
        )
        private val PARTICLE_KEYWORDS = listOf(
            "rock", "metal", "pop", "dance", "hip-hop", "hip hop", "rap", "punk",
        )
    }
}
