package com.aurora.player.designsystem.theme

import androidx.compose.ui.graphics.Color

// Dark theme (domyślny) — patrz DESIGN.md sekcja 2.1
val AuroraBackgroundTop = Color(0xFF0A0A0F)
val AuroraBackgroundBottom = Color(0xFF121218)
val AuroraSurfaceElevated = Color(0xFF16161D)
val AuroraBorderOnSurface = Color(0x14FFFFFF) // White @ 8%

val AuroraTextPrimary = Color(0xFFF2F2F0)
val AuroraTextSecondary = Color(0x99F2F2F0) // @ 60%
val AuroraTextTertiary = Color(0x5FF2F2F0) // @ 37%

val AuroraAccentFallback = Color(0xFF6C5CE7) // indygo-fiolet, gdy brak okładki
val AuroraError = Color(0xFFE5484D)

// Light theme (wtórny)
val AuroraLightBackground = Color(0xFFFAFAF8)
val AuroraLightSurface = Color(0xFFFFFFFF)
val AuroraLightBorderOnSurface = Color(0x0F000000) // Black @ 6%
val AuroraLightTextPrimary = Color(0xFF16161D)
val AuroraLightTextSecondary = Color(0x9916161D)

// Kolory domen treści (Etap 37) — stłumione/pastelowe, żeby nie kolidować z "dużo
// światła/whitespace" z sekcji 2; Now Playing świadomie NIE korzysta z tej palety (zostaje przy
// dynamicznym akcencie z okładki, patrz DESIGN.md sekcja 2.1) — to inny kontekst ekranu.
val DomainColorBiblioteka = Color(0xFF8FA8FF) // stonowany niebieski-indygo
val DomainColorPlaylisty = Color(0xFFB58FFF) // stonowany fiolet
val DomainColorGenius = Color(0xFFFF9ECF) // stonowany róż
val DomainColorRadio = Color(0xFFFF9E7A) // stonowana koral/pomarańcz
val DomainColorPodkasty = Color(0xFFFFD36E) // stonowany bursztyn
val DomainColorAudiobooki = Color(0xFF8FE0C8) // stonowana zieleń morska
val DomainColorMuzykaNiezalezna = Color(0xFF8FD6FF) // stonowany błękit
val DomainColorArchiwum = Color(0xFFD8C08F) // stonowany piaskowy/sepia

// Kolory "vivid" domen treści (Etap 37, poprawka po feedbacku: "ma być kolorowo, spójnie,
// czytelnie" wzorem mozaiki kafli Spotify Wrapped) — pełne, nasycone barwy przeznaczone WYŁĄCZNIE
// pod tła całych kart (ColorfulMosaicTile na Home/Odkrywaj). Powyższe stonowane `DomainColorXxx`
// zostają bez zmian tam, gdzie już działają jako tint małej ikony/tekstu na ciemnym tle (Szukaj —
// SearchScreen, ComingSoonScreen) — to inny kontekst użycia, nie migracja jednego na drugie.
// 8 wyraźnie różnych odcieni (niebieski/fiolet/magenta/czerwono-pomarańcz/bursztyn/turkus/granat/
// terakota), żaden nie jest wariantem tego samego hue co inny.
val DomainColorBibliotekaVivid = Color(0xFF2F5CFF) // nasycony niebieski-indygo
val DomainColorPlaylistyVivid = Color(0xFF7B2FF7) // nasycony fiolet
val DomainColorGeniusVivid = Color(0xFFFF2D87) // nasycona magenta/róż
val DomainColorRadioVivid = Color(0xFFFF4433) // nasycony czerwono-pomarańcz
val DomainColorPodkastyVivid = Color(0xFFFFC107) // jaskrawy bursztyn/żółty
val DomainColorAudiobookiVivid = Color(0xFF00BFA6) // nasycony turkus
val DomainColorMuzykaNiezaleznaVivid = Color(0xFF1E2A5E) // granat (ciemne tło, jasny tekst)
val DomainColorArchiwumVivid = Color(0xFFC97B3D) // terakota/sepia
