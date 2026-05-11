package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.runtime.Immutable

/**
 * Sensitivity profiles for the swipe-to-dismiss gesture.
 *
 * Different screens warrant different "how easy is it to dismiss" thresholds:
 * - [Sensitive]: image preview, media gallery — quick flicks.
 * - [Default]: regular content screens.
 * - [Conservative]: forms, payment, account creation — minimise accidental dismiss.
 *
 * @property distanceFraction Fraction of screen width the foreground must be dragged past
 * for the dismiss to commit on release.
 * @property velocityDp Velocity threshold (in dp / second) above which a flick commits the
 * dismiss regardless of distance. Density conversion is handled by
 * [SwipeToDismissLayout] via `Dp.toPx()`, which yields the device-correct px/s threshold.
 */
@Immutable
enum class SwipeSensitivity(val distanceFraction: Float, val velocityDp: Int) {
    Sensitive(distanceFraction = 0.20f, velocityDp = 800),
    Default(distanceFraction = 0.35f, velocityDp = 1500),
    Conservative(distanceFraction = 0.50f, velocityDp = 2000),
}

internal const val SWIPE_SENSITIVITY = "swipeSensitivity"

/**
 * Metadata helper for the per-entry [SwipeSensitivity] profile. Mirrors the pattern of
 * [SwipeEdgeGate] / [FreezeBackgroundWhileIdle]: write into entry metadata at registration
 * time, read it back inside [SwipeToDismissSceneStrategy].
 */
object SwipeDismissSensitivity {

    /**
     * Returns entry metadata that carries [sensitivity]. Omits the key when the value matches
     * [SwipeSensitivity.Default] to keep metadata maps tidy at default call sites.
     */
    fun metadata(sensitivity: SwipeSensitivity): Map<String, Any> =
        if (sensitivity == SwipeSensitivity.Default) {
            emptyMap()
        } else {
            mapOf(SWIPE_SENSITIVITY to sensitivity)
        }

    /**
     * Reads the sensitivity from entry metadata. Returns [SwipeSensitivity.Default] when the
     * key is absent or holds a non-[SwipeSensitivity] value.
     */
    fun from(metadata: Map<String, Any?>): SwipeSensitivity =
        metadata[SWIPE_SENSITIVITY] as? SwipeSensitivity ?: SwipeSensitivity.Default
}
