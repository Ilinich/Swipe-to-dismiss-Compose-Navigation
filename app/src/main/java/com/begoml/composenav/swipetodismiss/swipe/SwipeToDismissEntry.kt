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
 */
inline fun <reified T : NavKey> EntryProviderScope<NavKey>.swipeToDismissHorizontalEntry(
    animationDuration: Int = 300,
    noinline content: @Composable (T) -> Unit,
) {
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
        } + SwipeToDismissSceneStrategy.enabled(),
        content = content,
    )
}
