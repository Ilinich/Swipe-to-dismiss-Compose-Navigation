package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeToDismissSceneStrategyTest {

    private val strategy = SwipeToDismissSceneStrategy()
    private val scope = SceneStrategyScope<NavKey>()

    private data object KeyA : NavKey
    private data object KeyB : NavKey
    private data object KeyC : NavKey

    private fun entry(
        key: NavKey,
        metadata: Map<String, Any> = emptyMap(),
    ): NavEntry<NavKey> = NavEntry(
        key = key,
        metadata = metadata,
        content = { @Composable {} },
    )

    @Test
    fun `empty stack returns null`() {
        with(strategy) {
            val scene = scope.calculateScene(emptyList())
            assertNull(scene)
        }
    }

    @Test
    fun `single-entry stack returns null`() {
        with(strategy) {
            val scene = scope.calculateScene(
                listOf(entry(KeyA, SwipeToDismissSceneStrategy.enabled())),
            )
            assertNull(scene)
        }
    }

    @Test
    fun `top entry without enabled metadata returns null`() {
        with(strategy) {
            val scene = scope.calculateScene(
                listOf(entry(KeyA), entry(KeyB)),
            )
            assertNull(scene)
        }
    }

    @Test
    fun `opt-in top with size-2 stack returns SwipeToDismissScene`() {
        with(strategy) {
            val scene = scope.calculateScene(
                listOf(entry(KeyA), entry(KeyB, SwipeToDismissSceneStrategy.enabled())),
            )

            assertNotNull(scene)
            assertTrue(scene is SwipeToDismissScene)
        }
    }

    @Test
    fun `freeze flag is propagated when present`() {
        with(strategy) {
            val metadata = SwipeToDismissSceneStrategy.enabled() + FreezeBackgroundWhileIdle.enabled()
            val scene = scope.calculateScene(
                listOf(entry(KeyA), entry(KeyB, metadata)),
            ) as SwipeToDismissScene

            assertTrue(scene.freezeBackgroundWhileIdle)
        }
    }

    @Test
    fun `freeze flag defaults to false when metadata absent`() {
        with(strategy) {
            val scene = scope.calculateScene(
                listOf(entry(KeyA), entry(KeyB, SwipeToDismissSceneStrategy.enabled())),
            ) as SwipeToDismissScene

            assertEquals(false, scene.freezeBackgroundWhileIdle)
        }
    }

    @Test
    fun `edge gate parameters propagated through metadata`() {
        with(strategy) {
            val metadata = SwipeToDismissSceneStrategy.enabled() +
                SwipeEdgeGate.metadata(edgeWidthDp = Dp(24f), swipeFromAnywhere = false)
            val scene = scope.calculateScene(
                listOf(entry(KeyA), entry(KeyB, metadata)),
            ) as SwipeToDismissScene

            assertEquals(Dp(24f), scene.edgeWidthDp)
            assertEquals(false, scene.swipeFromAnywhere)
        }
    }

    @Test
    fun `swipeFromAnywhere true is propagated`() {
        with(strategy) {
            val metadata = SwipeToDismissSceneStrategy.enabled() +
                SwipeEdgeGate.metadata(edgeWidthDp = null, swipeFromAnywhere = true)
            val scene = scope.calculateScene(
                listOf(entry(KeyA), entry(KeyB, metadata)),
            ) as SwipeToDismissScene

            assertNull(scene.edgeWidthDp)
            assertEquals(true, scene.swipeFromAnywhere)
        }
    }

    @Test
    fun `previousEntry is the entry at index size-2`() {
        val a = entry(KeyA)
        val b = entry(KeyB)
        val c = entry(KeyC, SwipeToDismissSceneStrategy.enabled())
        with(strategy) {
            val scene = scope.calculateScene(listOf(a, b, c)) as SwipeToDismissScene
            assertSame(b, scene.previousEntry)
            assertSame(c, scene.currentEntry)
        }
    }

    @Test
    fun `entries contains only currentEntry to avoid double composition`() {
        val a = entry(KeyA)
        val b = entry(KeyB, SwipeToDismissSceneStrategy.enabled())
        with(strategy) {
            val scene = scope.calculateScene(listOf(a, b)) as SwipeToDismissScene
            assertEquals(listOf(b), scene.entries)
        }
    }

    @Test
    fun `previousEntries excludes the top entry`() {
        val a = entry(KeyA)
        val b = entry(KeyB)
        val c = entry(KeyC, SwipeToDismissSceneStrategy.enabled())
        with(strategy) {
            val scene = scope.calculateScene(listOf(a, b, c)) as SwipeToDismissScene
            assertEquals(listOf(a, b), scene.previousEntries)
        }
    }
}
