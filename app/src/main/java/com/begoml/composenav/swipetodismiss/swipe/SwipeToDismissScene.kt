package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay

/**
 * A [Scene] that hosts two navigation entries inside a [SwipeToDismissLayout]:
 * the [previousEntry] as the background and the [currentEntry] as the swipeable foreground.
 *
 * Created by [SwipeToDismissSceneStrategy] when the top entry is marked with
 * [SwipeToDismissSceneStrategy.enabled].
 *
 * Pop transitions are set to [EnterTransition.None] / [ExitTransition.None] because
 * the dismiss animation is handled entirely by [SwipeToDismissLayout] itself.
 */
internal data class SwipeToDismissScene(
    override val key: Any,
    val previousEntry: NavEntry<NavKey>,
    val currentEntry: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
    val freezeBackgroundWhileIdle: Boolean,
    val edgeWidthDp: Dp?,
    val swipeFromAnywhere: Boolean,
    val sensitivity: SwipeSensitivity,
    val onBack: () -> Unit,
) : Scene<NavKey> {

    override val entries: List<NavEntry<NavKey>> = listOf(currentEntry)

    override val content: @Composable () -> Unit = {
        SwipeToDismissLayout(
            onDismiss = onBack,
            backgroundContent = { previousEntry.Content() },
            foregroundContent = { currentEntry.Content() },
            freezeBackgroundWhileIdle = freezeBackgroundWhileIdle,
            edgeWidthDp = edgeWidthDp,
            swipeFromAnywhere = swipeFromAnywhere,
            sensitivity = sensitivity,
        )
    }

    override val metadata: Map<String, Any> =
        currentEntry.metadata + NavDisplay.popTransitionSpec {
            ContentTransform(
                targetContentEnter = EnterTransition.None,
                initialContentExit = ExitTransition.None,
            )
        }
}
