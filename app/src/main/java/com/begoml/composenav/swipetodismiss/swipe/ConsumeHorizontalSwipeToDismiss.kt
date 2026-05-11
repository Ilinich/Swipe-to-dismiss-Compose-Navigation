package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Unconditionally consumes horizontal scroll/fling leftover from a child scrollable so it does
 * not propagate up to [SwipeToDismissLayout]. Use when the screen must never be dismissed by
 * overscrolling a horizontal list (e.g. horizontal scroll is a primary interaction).
 *
 * For the iOS-native behavior where the dismiss gesture only activates when the list is already
 * at its start, use [consumeHorizontalSwipeToDismissWhenNotAtStart].
 */
@Composable
fun Modifier.consumeHorizontalSwipeToDismiss(): Modifier {
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(available.x, 0f)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(available.x, 0f)
        }
    }
    return this.nestedScroll(connection)
}

/**
 * Consumes horizontal scroll/fling leftover from a child scrollable so it does not propagate up
 * to [SwipeToDismissLayout], but only when the gesture started somewhere other than the very
 * beginning of the list.
 *
 * This matches iOS-native behavior and [androidx.compose.foundation.pager.HorizontalPager]'s
 * implicit behavior: if the list is already at its start when the user begins swiping, the
 * horizontal swipe dismisses the screen; if the list is somewhere in the middle, the same swipe
 * just scrolls the list back to its start, and the screen stays.
 *
 * The decision ("allow dismiss" vs. "block dismiss") is captured once on the first UserInput
 * event of a gesture and stays fixed for the duration of that gesture — so scrolling the list
 * all the way to the start mid-gesture does not suddenly unlock the dismiss.
 *
 * @param state Any scrollable state that supports [ScrollableState.canScrollBackward] —
 *   `LazyListState`, `PagerState`, `ScrollState`, etc.
 */
@Composable
fun Modifier.consumeHorizontalSwipeToDismissWhenNotAtStart(
    state: ScrollableState,
): Modifier {
    val connection = remember(state) {
        object : NestedScrollConnection {
            private var blockForThisGesture: Boolean? = null

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (
                    source == NestedScrollSource.UserInput &&
                    available.x != 0f &&
                    blockForThisGesture == null
                ) {
                    blockForThisGesture = state.canScrollBackward
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                return if (blockForThisGesture == true) {
                    Offset(available.x, 0f)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                val block = blockForThisGesture == true
                blockForThisGesture = null
                return if (block) Velocity(available.x, 0f) else Velocity.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}
