package com.vortessence.mahjong.ui.components

/**
 * How finely the fire's long shadows are traced across the board: [FULL] steps along each ray, or
 * [LIGHT], 40% less of that work, for a phone the first-game check found close to the line
 * (PerfCheck.PASSED_CLOSE, or one that struggled and kept its effects): set by itself, never
 * asked. Measured 2026-11-13 (OrientationPreview, Nine Dragon Pagoda by the fire): 24 steps
 * differ visibly from 40 in 0.7% of pixels on its side, 0.1% upright; 16 left a fine hatching
 * along the shadows' edges and lost some of their depth.
 */
object FireShadows {
    const val FULL = 40f
    const val LIGHT = 24f

    @Volatile
    var light = false

    // Test builds can try the light trace on any phone (perfOff=fullshadows)
    val steps: Float get() = if (light || PerfSwitches.isOff("fullshadows")) LIGHT else FULL
}
