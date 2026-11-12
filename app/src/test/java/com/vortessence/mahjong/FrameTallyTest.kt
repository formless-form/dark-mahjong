package com.vortessence.mahjong

import com.vortessence.mahjong.perf.FrameTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTallyTest {
    private val ms = 1_000_000L
    private val deadline = 16_666_667L

    private fun tally(frames: List<Long>, needed: Int = frames.size) =
        FrameTally(needed).apply { frames.forEach { add(it, deadline) } }

    @Test
    fun noVerdictUntilEnoughFrames() {
        assertNull(tally(List(99) { 5 * ms }, needed = 100).verdict())
    }

    @Test
    fun aFastPhoneKeepsItsEffects() {
        // Mostly quick, with the odd hitch
        val frames = List(600) { i -> if (i % 25 == 0) 40 * ms else 8 * ms }
        val v = tally(frames).verdict()!!
        assertFalse(v.struggling)
        assertTrue(v.fps >= 55)
    }

    @Test
    fun aQuarterLateIsStruggling() {
        val frames = List(600) { i -> if (i % 4 == 0) 20 * ms else 10 * ms }
        assertTrue(tally(frames).verdict()!!.struggling)
    }

    @Test
    fun aTenthVeryLateIsStruggling() {
        val frames = List(600) { i -> if (i % 10 == 0) 45 * ms else 10 * ms }
        assertTrue(tally(frames).verdict()!!.struggling)
    }

    @Test
    fun justUnderBothLinesIsFine() {
        // 20% late, of which 8% more than twice late
        val frames = List(600) { i ->
            when (i % 25) {
                0, 1 -> 40 * ms
                2, 3, 4 -> 20 * ms
                else -> 10 * ms
            }
        }
        assertFalse(tally(frames).verdict()!!.struggling)
    }

    @Test
    fun aSlowPhoneReportsItsRate() {
        // Every frame takes two and a bit refreshes: three on screen, 20 frames a second
        val v = tally(List(600) { 40 * ms }).verdict()!!
        assertTrue(v.struggling)
        assertEquals(20, v.fps)
    }

    @Test
    fun aLongStallCountsOnlyAFewRefreshes() {
        val frames = List(599) { 8 * ms } + 5_000 * ms
        val v = tally(frames).verdict()!!
        assertFalse(v.struggling)
        assertTrue(v.fps >= 57)
    }

    @Test
    fun aVerySlowPhoneIsDecidedOnTime() {
        // 600 ms a frame: 75 frames is 45 seconds
        val t = FrameTally()
        repeat(74) { t.add(600 * ms, deadline) }
        assertNull(t.verdict())
        t.add(600 * ms, deadline)
        val v = t.verdict()!!
        assertTrue(v.struggling)
        assertEquals(2, v.fps)
    }

    @Test
    fun aFewSlowFramesAloneDecideNothing() {
        // A long stall or two is not enough to judge by
        val t = FrameTally()
        repeat(59) { t.add(1_000 * ms, deadline) }
        assertNull(t.verdict())
    }
}
