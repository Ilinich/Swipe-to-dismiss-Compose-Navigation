package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedScrollSwipeArbiterTest {

    private val slop = 10f

    private fun arbiter() = NestedScrollSwipeArbiter(slop)

    @Test
    fun `does not arm below the slop`() {
        val arbiter = arbiter()

        assertFalse(arbiter.shouldArm(consumed = Offset.Zero, available = Offset(4f, 0f)))
    }

    @Test
    fun `arms on a rightward gesture past the slop`() {
        val arbiter = arbiter()

        assertTrue(arbiter.shouldArm(consumed = Offset.Zero, available = Offset(12f, 0f)))
    }

    @Test
    fun `accumulates samples until the slop is crossed`() {
        val arbiter = arbiter()

        val beforeSlop = (1..3).map { arbiter.shouldArm(Offset.Zero, Offset(3f, 0f)) }

        assertFalse(beforeSlop.any { it })
        assertTrue(arbiter.shouldArm(Offset.Zero, Offset(3f, 0f)))
    }

    @Test
    fun `does not arm on a leftward gesture`() {
        val arbiter = arbiter()

        assertFalse(arbiter.shouldArm(consumed = Offset.Zero, available = Offset(-12f, 0f)))
    }

    @Test
    fun `does not arm while the child still consumes the whole delta`() {
        val arbiter = arbiter()

        assertFalse(arbiter.shouldArm(consumed = Offset(20f, 0f), available = Offset.Zero))
    }

    @Test
    fun `a child that scrolls the whole gesture never hands it over`() {
        val arbiter = arbiter()

        val armed = (1..20).map { arbiter.shouldArm(Offset(30f, 0f), Offset.Zero) }

        assertFalse(armed.any { it })
    }

    @Test
    fun `arms once the child runs out of room and leaves a remainder`() {
        val arbiter = arbiter()
        repeat(3) { arbiter.shouldArm(consumed = Offset(20f, 0f), available = Offset.Zero) }

        assertTrue(arbiter.shouldArm(consumed = Offset(5f, 0f), available = Offset(15f, 0f)))
    }

    @Test
    fun `does not arm on a vertical scroll that leaks a horizontal remainder`() {
        val arbiter = arbiter()

        assertFalse(arbiter.shouldArm(consumed = Offset(0f, 30f), available = Offset(2f, 0f)))
    }

    @Test
    fun `a rejected gesture never arms even when it later turns rightward`() {
        val arbiter = arbiter()
        arbiter.shouldArm(consumed = Offset(0f, 40f), available = Offset(1f, 0f))

        val armed = (1..10).map { arbiter.shouldArm(Offset.Zero, Offset(20f, 0f)) }

        assertFalse(armed.any { it })
    }

    @Test
    fun `an armed gesture stays armed when it later turns vertical`() {
        val arbiter = arbiter()
        arbiter.shouldArm(consumed = Offset.Zero, available = Offset(20f, 0f))

        assertTrue(arbiter.shouldArm(consumed = Offset(0f, 90f), available = Offset(5f, 0f)))
    }

    @Test
    fun `opposite horizontal samples cancel out instead of accumulating`() {
        val arbiter = arbiter()

        arbiter.shouldArm(consumed = Offset.Zero, available = Offset(6f, 0f))

        assertFalse(arbiter.shouldArm(consumed = Offset.Zero, available = Offset(-6f, 0f)))
    }

    @Test
    fun `reset lets the next gesture be judged from scratch`() {
        val arbiter = arbiter()
        arbiter.shouldArm(consumed = Offset(0f, 40f), available = Offset.Zero)

        arbiter.reset()

        assertTrue(arbiter.shouldArm(consumed = Offset.Zero, available = Offset(12f, 0f)))
    }

    @Test
    fun `a diagonal gesture is decided by the dominant axis`() {
        val steeper = arbiter().shouldArm(consumed = Offset(0f, 20f), available = Offset(15f, 0f))
        val shallower = arbiter().shouldArm(consumed = Offset(0f, 15f), available = Offset(20f, 0f))

        assertEquals(false, steeper)
        assertEquals(true, shallower)
    }
}
