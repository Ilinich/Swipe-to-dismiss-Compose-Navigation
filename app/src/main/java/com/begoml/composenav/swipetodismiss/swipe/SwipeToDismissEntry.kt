package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay

/**
 * Registers a navigation entry with horizontal slide-in transition and iOS-style
 * swipe-to-dismiss on pop.
 *
 * Push: slides in from the right. Pop via swipe gesture: handled by [SwipeToDismissLayout]
 * (translate + corner rounding + snapshot caching). Pop via back button: also routed through
 * the layout's `onDismiss` callback (transition metadata is None/None to avoid double animation).
 *
 * @param pushAnimationDuration Duration in ms of the slide-in / slide-out animation when this
 * entry is pushed onto / popped off via system back. Does NOT affect the swipe-dismiss animation
 * (which is driven internally by [SwipeToDismissLayout] with a fixed spec).
 * @param freezeBackgroundWhileIdle When `true` (default), the previous entry's draw and measure
 * are skipped while this entry is on top, idle (no swipe in progress), and fully covers the
 * screen. Composition stays alive — `LaunchedEffect` / `DisposableEffect` are not torn down. Set
 * to `false` when:
 *  - Background contains `SurfaceView` / `TextureView` / `GLSurfaceView` / `WebView` / camera
 *    preview / video player — those live on separate hardware planes and won't be captured by
 *    the Compose snapshot, causing visual glitches during swipe-parallax.
 *  - Foreground is NOT fully opaque — semi-transparent foregrounds will show black/empty
 *    background while idle because draw is skipped.
 *  - Background must show fresh data immediately on return (live counter, video preview).
 *
 * See [FreezeBackgroundWhileIdle] for the full invariants list.
 *
 * @param edgeWidthDp Width of the left-edge zone (in dp from screen left) where a horizontal
 * swipe will trigger dismiss. `null` means full-screen — swipe activates anywhere. When set,
 * touches outside this zone are ignored and propagate to children. Default `null` for backward
 * compatibility; recommended value `(screenWidth * 0.2f).dp` after UX validation.
 * @param swipeFromAnywhere When `true`, ignores [edgeWidthDp] and accepts swipe from anywhere.
 * Use sparingly — for full-screen image preview / media gallery where any rightward swipe
 * should dismiss. Requires explicit comment explaining justification at call site.
 * @param sensitivity Distance / velocity thresholds for committing the dismiss. See
 * [SwipeSensitivity] — [SwipeSensitivity.Sensitive] for media gallery / image preview,
 * [SwipeSensitivity.Default] for regular content, [SwipeSensitivity.Conservative] for forms /
 * payment / account creation where accidental dismiss must be minimised.
 */
inline fun <reified T : NavKey> EntryProviderScope<NavKey>.swipeToDismissHorizontalEntry(
    pushAnimationDuration: Int = 300,
    freezeBackgroundWhileIdle: Boolean = true,
    edgeWidthDp: Dp? = null,
    swipeFromAnywhere: Boolean = false,
    sensitivity: SwipeSensitivity = SwipeSensitivity.Default,
    noinline content: @Composable (T) -> Unit,
) {
    val baseMetadata = NavDisplay.transitionSpec {
        slideIntoContainer(
            towards = SlideDirection.Start,
            animationSpec = tween(durationMillis = pushAnimationDuration, easing = LinearEasing),
        ) togetherWith slideOutOfContainer(
            towards = SlideDirection.Start,
            animationSpec = tween(durationMillis = pushAnimationDuration, easing = LinearEasing),
        )
    } + NavDisplay.popTransitionSpec {
        ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = ExitTransition.None,
        )
    } + SwipeToDismissSceneStrategy.enabled() +
        SwipeEdgeGate.metadata(edgeWidthDp = edgeWidthDp, swipeFromAnywhere = swipeFromAnywhere) +
        SwipeDismissSensitivity.metadata(sensitivity)

    val freezeMetadata: Map<String, Any> = if (freezeBackgroundWhileIdle) {
        FreezeBackgroundWhileIdle.enabled()
    } else {
        emptyMap()
    }

    entry<T>(
        metadata = baseMetadata + freezeMetadata,
        content = content,
    )
}
