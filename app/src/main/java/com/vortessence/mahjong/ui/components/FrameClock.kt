package com.vortessence.mahjong.ui.components

/**
 * Seconds for the firelight and the hint's pulse, from a frame's time. Frame times count from
 * the phone's start, and in a Float seconds since then lose their fraction within a few days of
 * uptime: the flicker went steppy. So they count from the app's first frame instead (less its
 * place within a 1000 s round, so each run starts the fire somewhere different), wrapping every
 * [WRAP_SECONDS], which a Float holds to well under a millisecond. Main thread only.
 */
internal object FrameClock {
    const val WRAP_SECONDS = 4096f
    private const val WRAP_NANOS = 4_096_000_000_000L
    private const val ROUND_NANOS = 1_000_000_000_000L

    private var origin = Long.MIN_VALUE

    fun seconds(frameNanos: Long): Float {
        if (origin == Long.MIN_VALUE) origin = frameNanos - Math.floorMod(frameNanos, ROUND_NANOS)
        return Math.floorMod(frameNanos - origin, WRAP_NANOS) / 1e9f
    }

    /**
     * Seconds from [then] to [now], both from [seconds], allowing for a wrap between them: a
     * little below 0 if [now] was in fact read a frame or two before [then].
     */
    fun since(now: Float, then: Float): Float {
        val d = now - then
        return when {
            d < -WRAP_SECONDS / 2 -> d + WRAP_SECONDS
            d > WRAP_SECONDS / 2 -> d - WRAP_SECONDS
            else -> d
        }
    }
}
