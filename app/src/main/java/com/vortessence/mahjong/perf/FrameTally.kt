package com.vortessence.mahjong.perf

import kotlin.math.roundToInt

/**
 * First-game frame check, judged by stutters as the eye sees them: a steady 20 or 30 fps looks
 * smooth (the firelight redraws at 20). A stutter is a frame past the phone's deadline that left
 * the last picture up over [STUTTER_GAP_NS] (below ~17 fps); a long gap before an on-time frame is
 * just a pause. Late frames are only logged.
 */
class FrameTally(private val framesNeeded: Int = FRAMES_NEEDED, private val timeEnoughNs: Long = TIME_ENOUGH_NS) {
    private var frames = 0
    private var late = 0
    private var veryLate = 0
    private var stutters = 0
    private var near = 0
    private var playedNs = 0L
    // Count of frames by ms since the previous one (last bucket: a second or more)
    private val gapsMs = IntArray(MAX_GAP_MS + 1)
    private var gaps = 0

    /** [totalNs] vsync to done; [deadlineNs] 0 means one refresh; [gapNs] since the last frame, 0 for the first. */
    @Synchronized
    fun add(totalNs: Long, deadlineNs: Long, refreshNs: Long, gapNs: Long) {
        val deadline = if (deadlineNs > 0) deadlineNs else refreshNs
        frames++
        if (totalNs > deadline) late++
        if (totalNs > deadline + refreshNs) veryLate++
        if (totalNs > deadline && gapNs > STUTTER_GAP_NS) stutters++
        if (totalNs > deadline && gapNs > NEAR_GAP_NS) near++
        if (gapNs > 0) {
            playedNs += minOf(gapNs, MAX_GAP_MS * 1_000_000L)
            gapsMs[(gapNs / 1_000_000L).toInt().coerceIn(0, MAX_GAP_MS)]++
            gaps++
        }
    }

    /** Null until enough frames are in; then whether the phone is struggling. */
    @Synchronized
    fun verdict(): FrameVerdict? {
        // Enough frames, or on a very slow phone enough time
        if (frames < framesNeeded && (playedNs < timeEnoughNs || frames < MIN_FRAMES)) return null
        val lateShare = late.toFloat() / frames
        val veryLateShare = veryLate.toFloat() / frames
        val stutterShare = stutters.toFloat() / frames
        val nearShare = near.toFloat() / frames
        val struggling = stutterShare >= STUTTER_SHARE
        return FrameVerdict(
            struggling = struggling,
            stutterShare = stutterShare,
            fps = fps(),
            frames = frames,
            lateShare = lateShare,
            veryLateShare = veryLateShare,
            nearShare = nearShare,
            close = !struggling && nearShare >= NEAR_SHARE,
        )
    }

    // From the median frame spacing, 60 at most
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
        // 30 s of play at the firelight's 20 fps
        const val FRAMES_NEEDED = 600
        // ...or 45 s with at least 60 frames
        const val TIME_ENOUGH_NS = 45_000_000_000L
        const val MIN_FRAMES = 60
        const val STUTTER_GAP_NS = 60_000_000L
        // Cautious, as a phone wrongly judged slow loses its firelight: 15% is choppy most of the
        // time. A Pixel 9 Pro on Quad, fire lit, looks smooth with 17% late, 6% a refresh late (2026-11-12)
        const val STUTTER_SHARE = 0.15f
        // Near the line: late after over 40 ms (below 25 fps). Alex's moto g05, a steady 20 fps with
        // no stutters, is near; a phone at 30 has margin (2026-11-13)
        const val NEAR_GAP_NS = 40_000_000L
        const val NEAR_SHARE = 0.15f
        private const val MAX_GAP_MS = 1000
    }
}

data class FrameVerdict(
    val struggling: Boolean,
    // Late, after a picture up over 60 ms
    val stutterShare: Float,
    val fps: Int,
    val frames: Int,
    val lateShare: Float,
    val veryLateShare: Float,
    // Late, after a picture up over 40 ms
    val nearShare: Float = 0f,
    // Kept up, but near the line: lighter fire shadows
    val close: Boolean = false,
)
