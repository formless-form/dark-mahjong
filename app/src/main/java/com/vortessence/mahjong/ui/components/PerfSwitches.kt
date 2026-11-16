package com.vortessence.mahjong.ui.components

/**
 * Test builds only: drawing stages switched off over adb (MainActivity's perfOff extra,
 * comma-separated) to measure their cost. Empty in Play builds.
 */
object PerfSwitches {
    @Volatile
    var off: Set<String> = emptySet()

    fun isOff(stage: String) = off.isNotEmpty() && stage in off

    /** Firelight fps override (perfFireFps); 0 = the game's own. */
    @Volatile
    var fireFps = 0

    /** Imperial shown as earned (perfImperial=earned, until perfImperial=locked). */
    @Volatile
    var imperialEarned = false

    /** Brocade table shown as earned (perfBrocade=earned, until perfBrocade=locked). */
    @Volatile
    var brocadeEarned = false
}
