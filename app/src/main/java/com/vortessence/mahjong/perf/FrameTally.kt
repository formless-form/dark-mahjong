package com.vortessence.mahjong.perf

import kotlin.math.roundToInt

/**
 * The first-game check's frame count, judged as the eye sees it: by stutters. Motion at a steady 20
 * or 30 frames a second looks smooth (the firelight itself redraws at 20), so a frame a refresh or
 * two behind goes unnoticed. A stutter is a frame that came late (past the deadline the phone
 * itself gave it) and left the last picture up more than [STUTTER_GAP_NS], a moment below about
 * 17 frames a second: the motion visibly jumps. A long gap before a frame on time is only a pause
 * in play. Late frames are counted too, for the log, but only stutters decide.
 */
class FrameTally(private val framesNeeded: Int = FRAMES_NEEDED, private val timeEnoughNs: Long = TIME_ENOUGH_NS) {
    private var frames = 0
    private var late = 0
    private var veryLate = 0
    private var stutters = 0
    private var playedNs = 0L
    // How many frames came this many milliseconds after the one before (the last, a second or more)
    private val gapsMs = IntArray(MAX_GAP_MS + 1)
    private var gaps = 0

    /**
     * One frame of play: [totalNs] from its vsync to done, against the [deadlineNs] the phone gave
     * it (one refresh, [refreshNs], where the phone gives none); [gapNs] since the frame before,
     * or 0 for the first.
     */
    @Synchronized
    fun add(totalNs: Long, deadlineNs: Long, refreshNs: Long, gapNs: Long) {
        val deadline = if (deadlineNs > 0) deadlineNs else refreshNs
        frames++
        if (totalNs > deadline) late++
        if (totalNs > deadline + refreshNs) veryLate++
        if (totalNs > deadline && gapNs > STUTTER_GAP_NS) stutters++
        if (gapNs > 0) {
            playedNs += minOf(gapNs, MAX_GAP_MS * 1_000_000L)
            gapsMs[(gapNs / 1_000_000L).toInt().coerceIn(0, MAX_GAP_MS)]++
            gaps++
        }
    }

    /** Null until enough frames are in; then whether the phone is struggling. */
    @Synchronized
    fun verdict(): FrameVerdict? {
        // Enough frames, or (on a phone so slow that they come few and far between) enough time
        if (frames < framesNeeded && (playedNs < timeEnoughNs || frames < MIN_FRAMES)) return null
        val lateShare = late.toFloat() / frames
        val veryLateShare = veryLate.toFloat() / frames
        val stutterShare = stutters.toFloat() / frames
        return FrameVerdict(
            struggling = stutterShare >= STUTTER_SHARE,
            stutterShare = stutterShare,
            fps = fps(),
            frames = frames,
            lateShare = lateShare,
            veryLateShare = veryLateShare,
        )
    }

    // The frame rate from the middle spacing of frames, 60 at most
    private fun fps(): Int {
        if (gaps == 0) return 0
        var seen = 0
        for (ms in gapsMs.indices) {
            seen += gapsMs[ms]
            if (seen * 2 >= gaps) return (1000f / (ms + 0.5f)).roundToInt().coerceIn(1, 60)
        }
        return 1
    }

    companion object {
        // 30 seconds of play with the firelight at 20 frames a second
        const val FRAMES_NEEDED = 600
        // ...or 45 seconds of play, with at least 60 frames in it
        const val TIME_ENOUGH_NS = 45_000_000_000L
        const val MIN_FRAMES = 60
        // A picture up longer than this, when the frame after it was late, is a stutter
        const val STUTTER_GAP_NS = 60_000_000L
        // Cautious, since a phone wrongly judged slow loses its firelight: a stutter in 15% of
        // frames is choppy most of the time, not the odd hitch. A Pixel 9 Pro playing Quad by
        // itself, fire lit, looks perfectly smooth with 17% of frames late and 6% more than a
        // refresh late (2026-11-12)
        const val STUTTER_SHARE = 0.15f
        private const val MAX_GAP_MS = 1000
    }
}

data class FrameVerdict(
    val struggling: Boolean,
    // Frames that came late and left the last picture up over 60 ms
    val stutterShare: Float,
    // Frames a second, from how far apart they came
    val fps: Int,
    val frames: Int,
    val lateShare: Float,
    val veryLateShare: Float,
)
