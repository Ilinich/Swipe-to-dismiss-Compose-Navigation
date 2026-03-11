package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import com.begoml.composenav.swipetodismiss.swipe.LocalSwipeToDismissActive

/**
 * Freezes the draw output of downstream modifiers when swipe-to-dismiss is active.
 *
 * While [com.begoml.composenav.swipetodismiss.swipe.LocalSwipeToDismissActive] is `false` (idle state), content is recorded into a
 * [GraphicsLayer][androidx.compose.ui.graphics.layer.GraphicsLayer] every frame and drawn
 * normally. Once swiping starts, the last recorded snapshot is replayed instead of calling
 * `drawContent()`, which prevents position-aware effects (e.g. Haze blur) from
 * recalculating against the moving/scaled coordinate space.
 *
 * **Modifier chain placement**: must be applied BEFORE `.hazeEffect()` so that the freeze
 * intercepts the draw call before Haze can query updated screen positions.
 *
 * ```
 * Modifier
 *     .clip(RoundedCornerShape(24.dp))
 *     .freezeDuringSwipeToDismiss()  // <-- captures hazeEffect output
 *     .hazeEffect(state = hazeState, style = hazeStyle)
 * ```
 */
@Composable
fun Modifier.freezeDuringSwipeToDismiss(): Modifier {
    val isSwiping = LocalSwipeToDismissActive.current
    val layer = rememberGraphicsLayer()
    val hasSnapshot = remember { mutableStateOf(false) }
    return this.then(
        Modifier.drawWithContent {
            if (!isSwiping) {
                layer.record(
                    size = IntSize(size.width.toInt(), size.height.toInt()),
                ) {
                    this@drawWithContent.drawContent()
                }
                drawLayer(layer)
                hasSnapshot.value = true
            } else if (hasSnapshot.value) {
                drawLayer(layer)
            }
        }
    )
}
