package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val SETTLE_TAIL_MS = 350L

/**
 * Observes whether a system predictive-back gesture is currently in progress.
 *
 * Reads [NavigationEventDispatcher.transitionState][androidx.navigationevent.NavigationEventDispatcher.transitionState]
 * — a passive [kotlinx.coroutines.flow.StateFlow] that mirrors the gesture without consuming it, so
 * NavDisplay's own predictive-pop handling is unaffected. The flag flips to `true` on gesture start
 * (before the first transformed frame) and back to `false` on commit or cancel, with a short tail
 * so the flag outlives the settle animation that follows the gesture.
 */
@Composable
fun rememberPredictiveBackInProgress(): State<Boolean> {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    return if (dispatcher == null) {
        remember { mutableStateOf(false) }
    } else {
        produceState(initialValue = false, dispatcher) {
            dispatcher.transitionState.collectLatest { state ->
                if (state is NavigationEventTransitionState.InProgress) {
                    value = true
                } else {
                    delay(SETTLE_TAIL_MS)
                    value = false
                }
            }
        }
    }
}
