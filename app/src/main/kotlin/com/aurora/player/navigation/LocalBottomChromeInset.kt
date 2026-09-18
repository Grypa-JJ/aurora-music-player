package com.aurora.player.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Realna wysokość pływającego mini-playera + dolnej nawigacji (patrz [AuroraNavHost]) — 0.dp gdy
 * pasek jest ukryty (Now Playing). `NavHost` jest tam pełnoekranowy (NIE przycięty przez
 * `Scaffold.innerPadding`), żeby treść realnie przewijała się POD pływającym paskiem i Haze miał
 * co rozmywać (bez tego pasek dostawał płaski, nierozmyty kolor — nic pod nim nigdy nie było
 * narysowane). Ekrany z przewijalną treścią dodają tę wartość do dolnego `contentPadding`/spacera,
 * żeby ostatni element nie chował się na stałe pod paskiem, a jednocześnie wciąż było go widać
 * (rozmytego) w trakcie przewijania — dokładnie tak jak w Spotify.
 */
val LocalBottomChromeInset = compositionLocalOf<Dp> { 0.dp }
