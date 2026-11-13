package com.vortessence.mahjong

import com.vortessence.mahjong.perf.FrameTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTallyTest {
    private val ms = 1_000_000L
    private val refresh = 16_666_667L

    // Frames as (time to done, deadline, gap since the last), all in ms
    private fun tally(frames: List<Triple<Long, Long, Long>>, needed: Int = frames.size) =
        FrameTally(needed).apply { frames.forEach { (t, d, g) -> add(t * ms, d * ms, refresh, g * ms) } }

    private fun frames(n: Int, f: (Int) -> Triple<Long, Long, Long>) = List(n, f)

    @Test
    fun noVerdictUntilEnoughFrames() {
        assertNull(tally(frames(99) { Triple(5L, 50L, 17L) }, needed = 100).verdict())
    }

    @Test
    fun overlappingFramesWithinTheirDeadlineAreFine() {
        // As on a Pixel: 42 ms start to finish, but a 50 ms deadline, one a refresh apart
        val v = tally(frames(600) { Triple(42L, 50L, 17L) }).verdict()!!
        assertFalse(v.struggling)
        assertTrue(v.fps >= 55)
    }

    @Test
    fun aPixelLikeMixPasses() {
        // 20% late, half of those by more than a refresh, the firelight's frames 50 ms apart
        val v = tally(frames(600) { i ->
            when (i % 10) {
                0 -> Triple(90L, 50L, 50L)
                1 -> Triple(55L, 50L, 33L)
                else -> Triple(40L, 50L, 17L)
            }
        }).verdict()!!
        assertFalse(v.struggling)
    }

    @Test
    fun aSteady20FramesASecondLooksSmooth() {
        // Every frame late, but each picture up only 50 ms
        assertFalse(tally(frames(600) { Triple(70L, 33L, 50L) }).verdict()!!.struggling)
    }

    @Test
    fun aSteady20FramesASecondIsNearTheLine() {
        // Alex's moto g05 by the fire: no stutters, but every picture up 50 ms
        val v = tally(frames(600) { Triple(70L, 33L, 50L) }).verdict()!!
        assertFalse(v.struggling)
        assertTrue(v.close)
    }

    @Test
    fun aSteady30FramesASecondHasAMargin() {
        val v = tally(frames(600) { Triple(45L, 33L, 33L) }).verdict()!!
        assertFalse(v.struggling)
        assertFalse(v.close)
    }

    @Test
    fun aPixelLikeMixHasAMargin() {
        val v = tally(frames(600) { i ->
            when (i % 10) {
                0 -> Triple(90L, 50L, 50L)
                1 -> Triple(55L, 50L, 33L)
                else -> Triple(40L, 50L, 17L)
            }
        }).verdict()!!
        assertFalse(v.close)
    }

    @Test
    fun strugglingIsNeverCalledClose() {
        assertFalse(tally(frames(600) { Triple(100L, 33L, 83L) }).verdict()!!.close)
    }

    @Test
    fun aSteady12FramesASecondIsStruggling() {
        val v = tally(frames(600) { Triple(100L, 33L, 83L) }).verdict()!!
        assertTrue(v.struggling)
        assertEquals(1f, v.stutterShare)
    }

    @Test
    fun aPauseBeforeAFrameOnTimeIsNoStutter() {
        // No firelight: frames only when something moves, seconds apart at times
        val v = tally(frames(600) { i -> if (i % 3 == 0) Triple(10L, 33L, 2000L) else Triple(10L, 33L, 17L) }).verdict()!!
        assertFalse(v.struggling)
        assertEquals(0f, v.stutterShare)
    }

    @Test
    fun theOddHitchIsFine() {
        val v = tally(frames(600) { i -> if (i % 10 == 0) Triple(120L, 33L, 100L) else Triple(20L, 33L, 17L) }).verdict()!!
        assertFalse(v.struggling)
    }

    @Test
    fun aHitchInFiveFramesIsStruggling() {
        val v = tally(frames(600) { i -> if (i % 5 == 0) Triple(120L, 33L, 100L) else Triple(20L, 33L, 17L) }).verdict()!!
        assertTrue(v.struggling)
    }

    @Test
    fun aVerySlowPhoneIsDecidedOnTime() {
        // 600 ms apart: 75 gaps is 45 seconds (the first frame has none)
        val t = FrameTally()
        repeat(75) { t.add(700 * ms, 150 * ms, refresh, if (it == 0) 0 else 600 * ms) }
        assertNull(t.verdict())
        t.add(700 * ms, 150 * ms, refresh, 600 * ms)
        val v = t.verdict()!!
        assertTrue(v.struggling)
        assertEquals(2, v.fps)
    }

    @Test
    fun aFewSlowFramesAloneDecideNothing() {
        val t = FrameTally()
        repeat(59) { t.add(1_000 * ms, 150 * ms, refresh, 1_000 * ms) }
        assertNull(t.verdict())
    }
}
