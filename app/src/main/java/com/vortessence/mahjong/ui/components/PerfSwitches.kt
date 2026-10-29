package com.vortessence.mahjong.ui.components

/**
 * Test builds only: drawing stages switched off over adb (MainActivity's perfOff extra, a
 * comma-separated list), to measure what each costs. Empty, and so never consulted, in Play builds.
 */
object PerfSwitches {
    @Volatile
    var off: Set<String> = emptySet()

    fun isOff(stage: String) = off.isNotEmpty() && stage in off

    /** The firelight's frames a second, if set (perfFireFps), for comparing rates by eye; 0 for the game's own. */
    @Volatile
    var fireFps = 0
}
