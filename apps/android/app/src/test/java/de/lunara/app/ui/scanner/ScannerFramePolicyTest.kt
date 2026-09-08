package de.lunara.app.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerFramePolicyTest {
    @Test
    fun `frame stays square centered and inside every available window`() {
        val sizes = listOf(160, 240, 320, 600, 800, 1_200)
        sizes.forEach { width ->
            sizes.forEach { height ->
                assertValidFrame(width, height, largeScreen = width >= 600)
            }
        }
        listOf(640 to 240, 600 to 320, 1_280 to 800, 420 to 240).forEach { (width, height) ->
            assertValidFrame(width, height, largeScreen = width >= 600)
        }
    }

    @Test
    fun `frame uses only the visible intersection of a center crop surface`() {
        val container = FrameBounds(0, 0, 600, 320)
        val surface = FrameBounds(-180, 20, 780, 300)
        val frame = ScannerFramePolicy.calculate(container, surface, density = 1f, largeScreen = true)

        assertEquals(frame.width, frame.height)
        assertTrue(frame.left >= 0)
        assertTrue(frame.top >= 20)
        assertTrue(frame.right <= 600)
        assertTrue(frame.bottom <= 300)
    }

    private fun assertValidFrame(width: Int, height: Int, largeScreen: Boolean) {
        val bounds = FrameBounds(0, 0, width, height)
        val frame = ScannerFramePolicy.calculate(bounds, bounds, density = 1f, largeScreen = largeScreen)

        assertEquals("$width x $height must stay square", frame.width, frame.height)
        assertTrue(frame.width > 0)
        assertTrue(frame.left >= bounds.left)
        assertTrue(frame.top >= bounds.top)
        assertTrue(frame.right <= bounds.right)
        assertTrue(frame.bottom <= bounds.bottom)
        assertTrue(kotlin.math.abs((frame.left + frame.right) - width) <= 1)
        assertTrue(kotlin.math.abs((frame.top + frame.bottom) - height) <= 1)
    }
}
