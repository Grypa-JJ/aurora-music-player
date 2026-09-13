package com.aurora.player.designsystem.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared element transition mini-player ↔ Now Playing — patrz DESIGN.md sekcja 2.4 pkt 7
 * ("najbardziej wow efekt premium playerów"). No-op (zwraca `this` bez zmian) gdy scope'y nie są
 * podane, więc komponenty (MiniPlayerBar, NowPlayingScreen) działają identycznie z i bez
 * `SharedTransitionLayout` w drzewie — nie ma twardej zależności od nawigacji.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementOrSelf(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: Any,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this
    val state = sharedTransitionScope.rememberSharedContentState(key = key)
    return with(sharedTransitionScope) {
        this@sharedElementOrSelf.sharedElement(state, animatedVisibilityScope)
    }
}
