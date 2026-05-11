package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeEdgeGateTest {

    @Test
    fun `metadata is empty when no flags set`() {
        val metadata = SwipeEdgeGate.metadata(edgeWidthDp = null, swipeFromAnywhere = false)
        assertTrue(metadata.isEmpty())
    }

    @Test
    fun `metadata stores edge width as float in dp units`() {
        val metadata = SwipeEdgeGate.metadata(edgeWidthDp = Dp(24f), swipeFromAnywhere = false)
        assertEquals(24f, metadata[SWIPE_EDGE_WIDTH_DP])
    }

    @Test
    fun `metadata stores swipeFromAnywhere only when true`() {
        val withFlag = SwipeEdgeGate.metadata(edgeWidthDp = null, swipeFromAnywhere = true)
        assertEquals(true, withFlag[SWIPE_FROM_ANYWHERE])

        val withoutFlag = SwipeEdgeGate.metadata(edgeWidthDp = null, swipeFromAnywhere = false)
        assertFalse(withoutFlag.containsKey(SWIPE_FROM_ANYWHERE))
    }

    @Test
    fun `edgeWidthDp returns null when key missing`() {
        assertNull(SwipeEdgeGate.edgeWidthDp(emptyMap()))
    }

    @Test
    fun `edgeWidthDp reconstructs Dp from float`() {
        val metadata = mapOf(SWIPE_EDGE_WIDTH_DP to 24f)
        assertEquals(Dp(24f), SwipeEdgeGate.edgeWidthDp(metadata))
    }

    @Test
    fun `edgeWidthDp returns null when value is non-float`() {
        assertNull(SwipeEdgeGate.edgeWidthDp(mapOf(SWIPE_EDGE_WIDTH_DP to "24")))
        assertNull(SwipeEdgeGate.edgeWidthDp(mapOf(SWIPE_EDGE_WIDTH_DP to 24)))
    }

    @Test
    fun `isSwipeFromAnywhere defaults to false`() {
        assertFalse(SwipeEdgeGate.isSwipeFromAnywhere(emptyMap()))
        assertFalse(SwipeEdgeGate.isSwipeFromAnywhere(mapOf(SWIPE_FROM_ANYWHERE to false)))
    }

    @Test
    fun `isSwipeFromAnywhere returns true when flag set`() {
        assertTrue(SwipeEdgeGate.isSwipeFromAnywhere(mapOf(SWIPE_FROM_ANYWHERE to true)))
    }

    @Test
    fun `isSwipeFromAnywhere ignores non-boolean values`() {
        assertFalse(SwipeEdgeGate.isSwipeFromAnywhere(mapOf(SWIPE_FROM_ANYWHERE to "true")))
    }
}
