package com.begoml.composenav.swipetodismiss.swipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeDismissSensitivityTest {

    @Test
    fun `metadata is empty for Default sensitivity`() {
        assertTrue(SwipeDismissSensitivity.metadata(SwipeSensitivity.Default).isEmpty())
    }

    @Test
    fun `metadata stores Sensitive profile under the namespaced key`() {
        val metadata = SwipeDismissSensitivity.metadata(SwipeSensitivity.Sensitive)
        assertEquals(SwipeSensitivity.Sensitive, metadata[SWIPE_SENSITIVITY])
    }

    @Test
    fun `metadata stores Conservative profile under the namespaced key`() {
        val metadata = SwipeDismissSensitivity.metadata(SwipeSensitivity.Conservative)
        assertEquals(SwipeSensitivity.Conservative, metadata[SWIPE_SENSITIVITY])
    }

    @Test
    fun `from defaults to Default when key missing`() {
        assertEquals(SwipeSensitivity.Default, SwipeDismissSensitivity.from(emptyMap()))
    }

    @Test
    fun `from defaults to Default when value is of wrong type`() {
        assertEquals(
            SwipeSensitivity.Default,
            SwipeDismissSensitivity.from(mapOf(SWIPE_SENSITIVITY to "Sensitive")),
        )
        assertEquals(
            SwipeSensitivity.Default,
            SwipeDismissSensitivity.from(mapOf(SWIPE_SENSITIVITY to 0.2f)),
        )
    }

    @Test
    fun `from round-trips Sensitive`() {
        val metadata = SwipeDismissSensitivity.metadata(SwipeSensitivity.Sensitive)
        assertEquals(SwipeSensitivity.Sensitive, SwipeDismissSensitivity.from(metadata))
    }

    @Test
    fun `from round-trips Conservative`() {
        val metadata = SwipeDismissSensitivity.metadata(SwipeSensitivity.Conservative)
        assertEquals(SwipeSensitivity.Conservative, SwipeDismissSensitivity.from(metadata))
    }
}
