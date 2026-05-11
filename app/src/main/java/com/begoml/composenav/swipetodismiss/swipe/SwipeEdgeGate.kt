package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.ui.unit.Dp

/**
 * Metadata helper for the edge-gate options of [SwipeToDismissLayout].
 *
 * Two parameters are exposed to call sites via [swipeToDismissHorizontalEntry] and propagated
 * through entry metadata to the layout:
 *
 * - `edgeWidthDp` — width of the left-edge zone where a horizontal swipe triggers dismiss.
 *   `null` means the gesture is accepted anywhere on the screen (legacy / full-screen mode).
 * - `swipeFromAnywhere` — when `true`, [edgeWidthDp] is ignored and the gesture works
 *   anywhere. Use only for screens where any rightward swipe should dismiss (full-screen
 *   image preview, media gallery). Must be justified at the call site.
 */
internal const val SWIPE_EDGE_WIDTH_DP = "swipeEdgeWidthDp"
internal const val SWIPE_FROM_ANYWHERE = "swipeFromAnywhere"

object SwipeEdgeGate {

    /**
     * Builds the edge-gate metadata map for an entry.
     */
    fun metadata(edgeWidthDp: Dp?, swipeFromAnywhere: Boolean): Map<String, Any> = buildMap {
        if (edgeWidthDp != null) put(SWIPE_EDGE_WIDTH_DP, edgeWidthDp.value)
        if (swipeFromAnywhere) put(SWIPE_FROM_ANYWHERE, true)
    }

    /**
     * Reads the edge-gate width from entry metadata. Returns `null` when key is absent
     * (which means full-screen / no edge constraint).
     */
    fun edgeWidthDp(metadata: Map<String, Any?>): Dp? {
        val value = metadata[SWIPE_EDGE_WIDTH_DP] as? Float ?: return null
        return Dp(value)
    }

    /**
     * Reads the swipe-from-anywhere opt-out flag. Defaults to `false`.
     */
    fun isSwipeFromAnywhere(metadata: Map<String, Any?>): Boolean =
        metadata[SWIPE_FROM_ANYWHERE] as? Boolean == true
}
