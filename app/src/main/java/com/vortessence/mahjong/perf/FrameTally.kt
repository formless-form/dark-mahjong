package com.vortessence.mahjong.perf

import kotlin.math.roundToInt

/**
 * The first-game check's frame count: how long each frame of play took to draw against the time it
 * had (one refresh of the screen). Counting how many frames came each second would say nothing,
 * since a still board only redraws as the firelight flickers (20 times a second) on any phone; how
 * many frames ran late, and by how much, tells a phone that keeps up from one that doesn't.
 */
class FrameTally(private val framesNeeded: Int = FRAMES_NEEDED, private val timeEnoughNs: Long = TIME_ENOUGH_NS) {
    private var frames = 0
    private var late = 0
    private var veryLate = 0
    private var refreshes = 0L
    private var deadlines = 0L
    // Time spent drawing, each frame at least its own budget: on a phone that struggles, frames
    // come one after another, so this runs close to the time played
    private var drawnNs = 0L

    /** One frame of play: [totalNs] to draw it, [deadlineNs] it had (a refresh, at most 60 Hz). */
    @Synchronized
    fun add(totalNs: Long, deadlineNs: Long) {
        val budget = if (deadlineNs > 0) deadlineNs else DEFAULT_DEADLINE_NS
        frames++
        deadlines += budget
        drawnNs += maxOf(totalNs, budget)
        if (totalNs > budget) late++
        if (totalNs > budget * 2) veryLate++
        // The refreshes it was on screen for (a long stall, such as a resume, counts as a few)
        refreshes += ((totalNs + budget - 1) / budget).coerceIn(1, MAX_REFRESHES_PER_FRAME)
    }

    /** Null until enough frames are in; then whether the phone is struggling. */
    @Synchronized
    fun verdict(): FrameVerdict? {
        // Enough frames, or (on a phone so slow that they come few and far between) enough time
        if (frames < framesNeeded && (drawnNs < timeEnoughNs || frames < MIN_FRAMES)) return null
        val lateShare = late.toFloat() / frames
        val veryLateShare = veryLate.toFloat() / frames
        val refreshRate = 1e9 / (deadlines.toDouble() / frames)
        // The rate it could keep up with while things moved, never more than the screen's own
        val fps = (refreshRate * frames / refreshes).roundToInt().coerceAtMost(refreshRate.roundToInt())
        return FrameVerdict(
            struggling = lateShare >= LATE_SHARE || veryLateShare >= VERY_LATE_SHARE,
            fps = fps,
            frames = frames,
            lateShare = lateShare,
            veryLateShare = veryLateShare,
        )
    }

    companion object {
        // 30 seconds of play with the firelight at 20 frames a second
        const val FRAMES_NEEDED = 600
        // ...or 45 seconds' drawing, with at least 60 frames in it
        const val TIME_ENOUGH_NS = 45_000_000_000L
        const val MIN_FRAMES = 60
        // A quarter of frames late, or a tenth more than twice late (the slowest tenth under 30 fps
        // at 60 Hz): judged by eye as laggy rather than the odd hitch
        const val LATE_SHARE = 0.25f
        const val VERY_LATE_SHARE = 0.10f
        private const val DEFAULT_DEADLINE_NS = 16_666_667L
        private const val MAX_REFRESHES_PER_FRAME = 30L
    }
}

data class FrameVerdict(
    val struggling: Boolean,
    // Frames a second it could draw while things moved
    val fps: Int,
    val frames: Int,
    val lateShare: Float,
    val veryLateShare: Float,
)
