package com.vortessence.mahjong.ui.components

/**
 * Float seconds for the firelight and hint pulse. Counted from the app's first frame (rounded down
 * to 1000 s, so each run starts the fire differently) and wrapped at [WRAP_SECONDS]: seconds since
 * boot lose their fraction after days of uptime, making the flicker steppy. Main thread only.
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

    /** [now] - [then], allowing for a wrap; slightly negative if [now] was read a frame earlier. */
    fun since(now: Float, then: Float): Float {
        val d = now - then
        return when {
            d < -WRAP_SECONDS / 2 -> d + WRAP_SECONDS
            d > WRAP_SECONDS / 2 -> d - WRAP_SECONDS
            else -> d
        }
    }
}
