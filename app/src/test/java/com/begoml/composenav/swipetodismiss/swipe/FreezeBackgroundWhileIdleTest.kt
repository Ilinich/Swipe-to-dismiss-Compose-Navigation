package com.begoml.composenav.swipetodismiss.swipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeBackgroundWhileIdleTest {

    @Test
    fun `enabled returns map with single boolean key`() {
        val metadata = FreezeBackgroundWhileIdle.enabled()
        assertEquals(1, metadata.size)
        assertEquals(true, metadata[FREEZE_BACKGROUND_WHILE_IDLE])
    }

    @Test
    fun `isEnabledIn returns true when key is true`() {
        assertTrue(FreezeBackgroundWhileIdle.isEnabledIn(mapOf(FREEZE_BACKGROUND_WHILE_IDLE to true)))
    }

    @Test
    fun `isEnabledIn returns false when key is false`() {
        assertFalse(FreezeBackgroundWhileIdle.isEnabledIn(mapOf(FREEZE_BACKGROUND_WHILE_IDLE to false)))
    }

    @Test
    fun `isEnabledIn returns false when key is missing`() {
        assertFalse(FreezeBackgroundWhileIdle.isEnabledIn(emptyMap()))
        assertFalse(FreezeBackgroundWhileIdle.isEnabledIn(mapOf("other" to true)))
    }

    @Test
    fun `isEnabledIn returns false when value is non-boolean`() {
        assertFalse(FreezeBackgroundWhileIdle.isEnabledIn(mapOf(FREEZE_BACKGROUND_WHILE_IDLE to "true")))
        assertFalse(FreezeBackgroundWhileIdle.isEnabledIn(mapOf(FREEZE_BACKGROUND_WHILE_IDLE to 1)))
    }

    @Test
    fun `isEnabledIn handles null value`() {
        assertFalse(FreezeBackgroundWhileIdle.isEnabledIn(mapOf(FREEZE_BACKGROUND_WHILE_IDLE to null)))
    }
}
