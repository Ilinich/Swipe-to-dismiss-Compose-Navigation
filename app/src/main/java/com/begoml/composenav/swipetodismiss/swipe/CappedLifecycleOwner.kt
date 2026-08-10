package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * A [LifecycleOwner] that mirrors a source lifecycle but never exceeds [maxState].
 *
 * Used to host the background entry of a [SwipeToDismissScene]. The background is rendered (for
 * the swipe parallax) so it must reach `STARTED` to keep started-scoped work alive, but it is not
 * the focused destination, so it must NOT reach `RESUMED`. Without the cap the background entry
 * inherits the host lifecycle (which reaches `RESUMED`), so its `ON_RESUME`-gated effects fire
 * while it sits behind the foreground — or re-fire when a full-screen overlay above the scene is
 * dismissed and the background re-composes — e.g. re-emitting a "screen shown" analytics
 * impression for a screen the user is not actually looking at.
 */
internal class CappedLifecycleOwner(
    private val maxState: Lifecycle.State = Lifecycle.State.STARTED,
) : LifecycleOwner {

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    fun sync(sourceState: Lifecycle.State) {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = minOf(sourceState, maxState)
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

/**
 * Remembers a [CappedLifecycleOwner] that tracks [source] (the host [LocalLifecycleOwner] by
 * default) but is capped at [Lifecycle.State.STARTED]. Provide it via `LocalLifecycleOwner` around
 * content that is shown as a non-focused background so that content sees `STARTED`, never
 * `RESUMED`.
 */
@Composable
internal fun rememberCappedLifecycleOwner(
    source: LifecycleOwner = LocalLifecycleOwner.current,
): LifecycleOwner {
    val owner = remember { CappedLifecycleOwner() }
    DisposableEffect(source) {
        owner.sync(source.lifecycle.currentState)
        val observer = LifecycleEventObserver { _, _ -> owner.sync(source.lifecycle.currentState) }
        source.lifecycle.addObserver(observer)
        onDispose {
            source.lifecycle.removeObserver(observer)
            owner.destroy()
        }
    }
    return owner
}
