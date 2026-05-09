package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay

/**
 * Registers a navigation entry with swipe-to-dismiss enabled.
 *
 * Push: slide in from the right.
 * Pop: handled entirely by [SwipeToDismissLayout] — transition metadata is set to None/None.
 *
 * @param animationDuration Push slide-in duration in ms.
 * @param freezeBackgroundWhileIdle When `true`, the background entry's draw and measure work is
 * skipped while this entry is on top, idle, and fully covers the screen. Background composition
 * stays alive — its `LaunchedEffect` / `DisposableEffect` are not torn down on foreground
 * touches, which preserves window-scoped state like input focus and IME visibility. See
 * [FreezeBackgroundWhileIdle] for invariants and limitations. Default `false`.
 */
inline fun <reified T : NavKey> EntryProviderScope<NavKey>.swipeToDismissHorizontalEntry(
    animationDuration: Int = 300,
    freezeBackgroundWhileIdle: Boolean = false,
    noinline content: @Composable (T) -> Unit,
) {
    val freezeMetadata: Map<String, Any> = if (freezeBackgroundWhileIdle) {
        FreezeBackgroundWhileIdle.enabled()
    } else {
        emptyMap()
    }
    entry<T>(
        metadata = NavDisplay.transitionSpec {
            slideIntoContainer(
                towards = SlideDirection.Start,
                animationSpec = tween(animationDuration, easing = LinearEasing),
            ) togetherWith slideOutOfContainer(
                towards = SlideDirection.Start,
                animationSpec = tween(animationDuration, easing = LinearEasing),
            )
        } + NavDisplay.popTransitionSpec {
            ContentTransform(
                targetContentEnter = EnterTransition.None,
                initialContentExit = ExitTransition.None,
            )
        } + SwipeToDismissSceneStrategy.enabled() + freezeMetadata,
        content = content,
    )
}
