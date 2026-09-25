package io.github.chinalwb.vocab.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/*
 * Card → entry "container transform". A card in the browse list/tile wall and the
 * EntryScreen it opens share two keys: the whole container ("entry-<anchor>") grows
 * into the page, and the title ("title-<anchor>") glides and scales into the heading.
 * Screens reach the scopes through these locals so no signature has to thread them.
 */

const val TRANSITION_MS = 400

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The nav destination's AnimatedContentScope — which side of the transition we're on. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val Bounds = BoundsTransform { _, _ -> tween(TRANSITION_MS, easing = FastOutSlowInEasing) }

/** The card/page container. [shape] clips it while it flies in the overlay. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedEntryContainer(anchor: String, shape: Shape = RectangleShape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val nav = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedEntryContainer.sharedBounds(
            rememberSharedContentState("entry-$anchor"),
            nav,
            boundsTransform = Bounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/**
 * The title text. A shared *element* (not bounds): only one copy is drawn for the whole
 * flight, so the word stays solid while it moves and grows instead of cross-fading.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedEntryTitle(anchor: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val nav = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedEntryTitle.sharedElement(
            rememberSharedContentState("title-$anchor"),
            nav,
            boundsTransform = Bounds,
        )
    }
}
