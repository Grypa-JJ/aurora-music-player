package com.aurora.player.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val AuroraDarkColorScheme = darkColorScheme(
    background = AuroraBackgroundTop,
    surface = AuroraSurfaceElevated,
    primary = AuroraAccentFallback,
    onBackground = AuroraTextPrimary,
    onSurface = AuroraTextPrimary,
    error = AuroraError,
)

private val AuroraLightColorScheme = lightColorScheme(
    background = AuroraLightBackground,
    surface = AuroraLightSurface,
    primary = AuroraAccentFallback,
    onBackground = AuroraLightTextPrimary,
    onSurface = AuroraLightTextPrimary,
    error = AuroraError,
)

/**
 * Motyw aplikacji. Dynamic Color (Material You) jest celowo NIEUŻYWANY — patrz DESIGN.md 2.1:
 * apka ma własną, statyczną tożsamość niezależną od tapety systemowej.
 */
@Composable
fun AuroraTheme(
    darkTheme: Boolean = true,
    tokens: AuroraTokens = AuroraTokens(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AuroraDarkColorScheme else AuroraLightColorScheme

    CompositionLocalProvider(LocalAuroraTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AuroraTypography,
            shapes = AuroraShapes,
            content = content,
        )
    }
}
