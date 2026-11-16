package com.vortessence.mahjong.ui.components

/**
 * Steps per ray for the fire's shadow trace. [LIGHT] is set automatically for phones the first-game
 * check found close to the line (PerfCheck.PASSED_CLOSE, or struggled but kept effects). Measured
 * 2026-11-13: 24 steps differ visibly from 40 in under 1% of pixels; 16 hatched the shadow edges.
 */
object FireShadows {
    const val FULL = 40f
    const val LIGHT = 24f

    @Volatile
    var light = false

    // perfOff=fullshadows forces it in test builds
    val steps: Float get() = if (light || PerfSwitches.isOff("fullshadows")) LIGHT else FULL
}
