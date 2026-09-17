package com.aurora.player.designsystem.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Jedno źródło prawdy dla 8 domen treści Aurory (Etap 37) — nazwa/ikona/kolor per domena,
 * reużywane przez Home/Odkrywaj/Szukaj, żeby nie rozsypywać tych samych literałów po kilku
 * ekranach. Audiobooki/MuzykaNiezalezna/Archiwum dostały realny backend (LibriVox/Jamendo/
 * Internet Archive) w tej samej rundzie — `isAvailable = true` dla wszystkich ośmiu.
 *
 * `accentColor` (stonowany) zostaje jak było — tint małej ikony/tekstu na ciemnym tle (Szukaj,
 * ComingSoonScreen). `vividColor` to nowe pole (poprawka po feedbacku "ma być kolorowo") pod
 * pełne, nasycone tło kafla mozaiki (`ColorfulMosaicTile` na Home/Odkrywaj) — patrz Color.kt.
 */
enum class ContentDomain(
    val label: String,
    val icon: ImageVector,
    val accentColor: Color,
    val vividColor: Color,
    val isAvailable: Boolean = true,
) {
    Biblioteka("Biblioteka", Icons.Filled.LibraryMusic, DomainColorBiblioteka, DomainColorBibliotekaVivid),
    Playlisty("Playlisty", Icons.Filled.QueueMusic, DomainColorPlaylisty, DomainColorPlaylistyVivid),
    Genius("Genius", Icons.Filled.AutoAwesome, DomainColorGenius, DomainColorGeniusVivid),
    Radio("Radio", Icons.Filled.Radio, DomainColorRadio, DomainColorRadioVivid),
    Podkasty("Podkasty", Icons.Filled.Podcasts, DomainColorPodkasty, DomainColorPodkastyVivid),
    Audiobooki("Audiobooki", Icons.Filled.MenuBook, DomainColorAudiobooki, DomainColorAudiobookiVivid),
    MuzykaNiezalezna(
        "Muzyka niezależna",
        Icons.Filled.Public,
        DomainColorMuzykaNiezalezna,
        DomainColorMuzykaNiezaleznaVivid,
    ),
    Archiwum("Archiwum", Icons.Filled.Museum, DomainColorArchiwum, DomainColorArchiwumVivid),
}
