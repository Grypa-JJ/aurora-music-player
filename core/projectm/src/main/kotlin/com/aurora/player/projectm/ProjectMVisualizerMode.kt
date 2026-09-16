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
    // Etap 20/21, zgłoszenie: dopasowanie trybu do gatunku (dawne `defaultForGenre`) w praktyce
    // zawężało losowanie do małych podfolderów (np. Ambient = tylko Hypnotic+Drawing, 174 z ~580
    // presetów) — im mniejsza pula, tym częściej trafiał się ten sam, źle dopasowany preset (patrz
    // DESIGN.md Etap 19/20: powtarzający się preset z motywem "M"). Zamiast dalej ręcznie
    // przeszukiwać setki plików w poszukiwaniu winowajcy, domyślny wybór to teraz zawsze ALL —
    // najszersza pula rozcieńcza każdy pojedynczy zły preset, a użytkownik nadal może ręcznie
    // wybrać Ambient/Spectrum/Particle przełącznikiem, jeśli akurat chce węższy nastrój.
    ALL("Wszystkie", null, Icons.Filled.Apps),
    AMBIENT("Ambient", listOf("Hypnotic", "Drawing"), Icons.Filled.BlurOn),
    SPECTRUM("Spectrum", listOf("Waveform"), Icons.Filled.GraphicEq),
    PARTICLE("Particle", listOf("Particles", "Sparkle", "Supernova"), Icons.Filled.Grain),
}
