package com.aurora.player.designsystem.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AuroraSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 16.dp,
    val l: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
)

/** Presety sprężynowe używane w całej apce zamiast tween() — patrz DESIGN.md 2.4 pkt 5. */
object AuroraMotion {
    fun <T> bouncy() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

    fun <T> smooth() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
    )
}

data class AuroraTokens(
    val spacing: AuroraSpacing = AuroraSpacing(),
)

val LocalAuroraTokens = staticCompositionLocalOf { AuroraTokens() }
