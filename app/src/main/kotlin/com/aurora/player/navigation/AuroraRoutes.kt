package com.aurora.player.navigation

import androidx.navigation.NavHostController

/**
 * Stałe tras `AuroraNavHost` — wydzielone z niego do osobnego pliku w Etapie 37, bo `AuroraBottomNav`
 * musi się do nich odwoływać, a `private const val` w Kotlinie jest widoczne tylko w PLIKU, nie w
 * całym pakiecie (mimo tego samego package). `internal` = widoczne w module `:app`, tak jak dotąd.
 */
internal const val ROUTE_HOME = "home"
internal const val ROUTE_LIBRARY = "library"
internal const val ROUTE_DISCOVER = "discover"
internal const val ROUTE_SEARCH = "search"
internal const val ROUTE_NOW_PLAYING = "now_playing"
internal const val ROUTE_GENIUS_MIXES = "genius_mixes"
internal const val ROUTE_GENIUS_MIX_PREVIEW = "genius_mix_preview/{mixIndex}"
internal const val ROUTE_LICENSES = "licenses"
internal const val ROUTE_FAVORITES = "favorites"
internal const val ROUTE_PLAYLISTS = "playlists"
internal const val ROUTE_PLAYLIST_DETAIL = "playlist/{playlistId}"
internal const val ROUTE_QUEUE = "queue"
internal const val ROUTE_ALBUM_DETAIL = "album/{albumName}/{albumArtist}"
internal const val ROUTE_ARTIST_DETAIL = "artist/{artistName}"
internal const val ROUTE_RADIO = "radio"
internal const val ROUTE_PODCASTS = "podcasts"
internal const val ROUTE_PODCAST_DETAIL = "podcast_detail/{feedUrl}"
internal const val ROUTE_ACCOUNT = "account"

/** Jeden wspólny ekran "wkrótce" — zostaje jako fallback dla ewentualnych przyszłych domen (Etap 37). */
internal const val ROUTE_COMING_SOON = "coming_soon/{domain}"

/** Audiobooki (LibriVox), Archiwum (Internet Archive), Muzyka niezależna (Jamendo) — Etap 37. */
internal const val ROUTE_AUDIOBOOKS = "audiobooks"
internal const val ROUTE_AUDIOBOOK_DETAIL = "audiobook_detail/{audiobookId}"
internal const val ROUTE_ARCHIVE = "archive"
internal const val ROUTE_ARCHIVE_ITEM_DETAIL = "archive_item_detail/{identifier}"
internal const val ROUTE_INDEPENDENT_MUSIC = "independent_music"

/** "Pobrane" — w trakcie pobierania + zapisane na stałe z Archiwum, patrz DownloadsScreen. */
internal const val ROUTE_DOWNLOADS = "downloads"

/** Trzy trasy hostowane przez [com.aurora.player.navigation.AuroraBottomNav] (Etap 39 — było
 *  cztery w Etapie 37, "Szukaj" przestała być osobną zakładką). */
internal val BOTTOM_NAV_ROUTES = setOf(ROUTE_HOME, ROUTE_LIBRARY, ROUTE_DISCOVER)

/**
 * Nawigacja między 4 zakładkami root — zachowuje stan każdej (scroll, wybrany LibraryTab) między
 * przełączeniami, zamiast stertować kolejne kopie tej samej trasy na backstacku. Używane zarówno
 * przez [AuroraBottomNav], jak i przez skróty domen na Home, które prowadzą do zakładki (dziś
 * tylko Biblioteka) zamiast do zwykłego ekranu push (Etap 37).
 */
internal fun NavHostController.navigateToTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(ROUTE_HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
