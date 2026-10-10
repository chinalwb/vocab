package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// "Liquid glass", after iOS 26: controls float over the content as frosted glass with a light
// rim; the content itself stays opaque. Haze blurs whatever is drawn under hazeSource(LocalHaze)
// on Android 12+; older versions get the fallback tint, a near-solid wash.

/** The screen's content, the thing the glass blurs. Null outside Home (no blur, tint only). */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun glassStyle(): HazeStyle {
    val dark = isSystemInDarkTheme()
    val ground = MaterialTheme.colorScheme.background
    return HazeStyle(
        backgroundColor = ground,
        tint = HazeTint(ground.copy(alpha = if (dark) 0.45f else 0.4f)),
        blurRadius = 24.dp,
        noiseFactor = 0.04f,
        fallbackTint = HazeTint(ground.copy(alpha = 0.94f)),
    )
}

/** The rim: brighter along the top edge, like light catching the glass. */
@Composable
fun glassRim(): Brush {
    val dark = isSystemInDarkTheme()
    return Brush.verticalGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
        else listOf(Color.White.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.06f)),
    )
}

/** Frosted glass in [shape], blurring what scrolls underneath. */
@Composable
fun Modifier.glass(shape: Shape, block: (HazeEffectScope.() -> Unit)? = null): Modifier =
    this.clip(shape)
        .hazeEffect(LocalHaze.current, glassStyle(), block)
        .border(1.dp, glassRim(), shape)

/**
 * The same look without the blur, for controls that sit inside the content (search, 筛选):
 * a glass effect there would sample itself, and there's only the plain ground behind anyway.
 */
@Composable
fun Modifier.glassFlat(shape: Shape): Modifier {
    val dark = isSystemInDarkTheme()
    val fill = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.7f)
    return this.clip(shape).background(fill, shape).border(1.dp, glassRim(), shape)
}
