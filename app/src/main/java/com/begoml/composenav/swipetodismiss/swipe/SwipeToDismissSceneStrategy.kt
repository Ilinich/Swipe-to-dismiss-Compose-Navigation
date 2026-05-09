package com.begoml.composenav.swipetodismiss.swipe

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import kotlin.text.get

/**
 * [SceneStrategy] that intercepts navigation entries marked with [enabled] metadata
 * and wraps them into a [SwipeToDismissScene].
 *
 * When the top entry has the [SWIPE_TO_DISMISS_ENABLED] flag, this strategy creates a scene
 * that renders both the previous and current entries inside [SwipeToDismissLayout],
 * enabling an iOS-style horizontal swipe-to-dismiss gesture.
 *
 * ## Usage
 *
 * Add this strategy to [NavDisplay.sceneStrategy] and mark entries with [enabled]:
 * ```
 * // In EntryProviderScope:
 * entry<MyKey>(
 *     content = { ... },
 *     metadata = NavDisplay.transitionSpec { ... }
 *         + SwipeToDismissSceneStrategy.enabled()
 * )
 *
 * // Or use the convenience extension:
 * swipeToDismissHorizontalEntry<MyKey> { key -> MyScreen(key) }
 * ```
 *
 * Requires at least 2 entries in the back stack (the current + the one behind it).
 */
class SwipeToDismissSceneStrategy : SceneStrategy<NavKey> {

    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? {
        if (entries.size < 2) return null

        val currentEntry = entries.last()
        if (currentEntry.metadata[SWIPE_TO_DISMISS_ENABLED] != true) return null

        val previousEntry = entries[entries.size - 2]

        return SwipeToDismissScene(
            key = currentEntry.contentKey,
            previousEntry = previousEntry,
            currentEntry = currentEntry,
            previousEntries = entries.dropLast(1),
            freezeBackgroundWhileIdle = FreezeBackgroundWhileIdle.isEnabledIn(currentEntry.metadata),
            onBack = onBack,
        )
    }

    companion object {
        internal const val SWIPE_TO_DISMISS_ENABLED = "swipeToDismissEnabled"

        /**
         * Returns entry metadata that enables swipe-to-dismiss for the annotated navigation entry.
         * Combine with other metadata via `+`:
         * ```
         * metadata = NavDisplay.transitionSpec { ... } + SwipeToDismissSceneStrategy.enabled()
         * ```
         */
        fun enabled(): Map<String, Any> = mapOf(SWIPE_TO_DISMISS_ENABLED to true)
    }
}
