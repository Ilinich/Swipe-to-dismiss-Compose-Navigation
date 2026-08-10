package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal that determines whether the swipe-to-dismiss gesture is wired up at all.
 *
 * `true` (default) — [SwipeToDismissScene] renders the foreground inside [SwipeToDismissLayout]
 * with gesture handler, GraphicsLayer snapshots, and parallax.
 *
 * `false` — [SwipeToDismissScene] bypasses the layout entirely: the foreground is rendered
 * directly via `currentEntry.Content()`, no gesture handler / GraphicsLayer is allocated. The
 * push slide animation still plays via NavDisplay's transitionSpec metadata; back navigation
 * works only via the system back button.
 *
 * ## Why the decision is not made here
 *
 * The navigation layer stays agnostic of *what drives* the fallback (device performance tier,
 * user preference, A/B flag, accessibility setting). Callers — typically the app shell — provide
 * this local with whatever policy applies. Example:
 *
 * ```
 * CompositionLocalProvider(
 *     LocalSwipeGestureAvailable provides (performanceTier != PerformanceTier.LOW)
 * ) {
 *     // NavDisplay(...)
 * }
 * ```
 *
 * Static because the consumer ([SwipeToDismissScene]'s content) needs to recompose from the top
 * of the scene tree when the value flips, which is the desired behaviour for what is effectively
 * a runtime feature gate.
 */
val LocalSwipeGestureAvailable = staticCompositionLocalOf { true }
