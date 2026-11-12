package com.vortessence.mahjong

import com.vortessence.mahjong.ui.components.LampColour
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LampColourTest {
    @Test
    fun at2700ItIsTheLampAsTuned() {
        assertArrayEquals(floatArrayOf(1f, 0.86f, 0.70f), LampColour.table(2700f), 1e-5f)
        assertArrayEquals(floatArrayOf(1f, 0.89f, 0.76f), LampColour.tiles(2700f), 1e-5f)
    }

    @Test
    fun itBurnsAt2000() {
        assertEquals(2000f, LampColour.DEFAULT_KELVIN)
        val table = LampColour.table(2000f)
        assertEquals(1f, table[0], 1e-6f)
        assertEquals(0.81f, table[1], 0.01f)
        assertEquals(0.57f, table[2], 0.01f)
    }

    @Test
    fun warmerIsLessGreenAndBlue() {
        var last = LampColour.table(3000f)
        for (k in listOf(2700f, 2400f, 2100f, 1800f)) {
            val c = LampColour.table(k)
            assertTrue("green at $k", c[1] < last[1])
            assertTrue("blue at $k", c[2] <= last[2])
            last = c
        }
    }
}
