package com.vortessence.mahjong.ui.components

import android.graphics.RuntimeShader
import kotlin.math.floor
import kotlin.math.sin

/**
 * This frame's table light: the lamp at [lamp] plus an off-screen fire (none when [power] is 0) at
 * plane ([u], [v]) in table heights, v up as in the table shader; [reach] is the squared distance
 * at which it halves. Recomputed every frame, as flames never hold still.
 */
class FlameLight(
    val u: Float,
    val v: Float,
    val power: Float,
    val reach: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val lamp: Float,
    // Above the table, in table heights: sets how long shadows reach
    val height: Float = 0.2f,
    // The average fire, without flicker, for anything that must hold still
    val steadyU: Float = u,
    val steadyV: Float = v,
    val steadyPower: Float = power,
    // This frame's, in table heights: the long shadows are traced from here
    val flameHeight: Float = 0.075f,
    // Pool of light's width over round: 1 upright, more for an on-side board's far-edge hearth
    val spread: Float = 1f,
) {
    /** Brightness at plane ([pu], [pv]), as the shaders' flameLight. */
    fun falloff(pu: Float, pv: Float, atU: Float = u, atV: Float = v, strength: Float = power): Float {
        val du = (pu - atU) / spread
        val dv = pv - atV
        return strength / (1f + (du * du + dv * dv) / reach)
    }

    fun setUniforms(s: RuntimeShader) {
        s.setFloatUniform("flame", u, v, power, reach)
        s.setFloatUniform("flameColour", r, g, b)
        s.setFloatUniform("lampLevel", lamp)
        s.setFloatUniform("flameHeight", flameHeight)
        s.setFloatUniform("flameSpread", spread)
        LampColour.setUniforms(s)
    }

    companion object {
        /** Average fire position, in table heights (as the shaders' q). */
        const val FIRE_U = -0.50f
        const val FIRE_V = 0.10f

        // On-side boards: the hearth is centred beyond the far edge (0.58), as far out as upright's
        // is past the left (~0.23), and twice as broad: a small one left the far end too dark
        private const val ABOVE_FIRE_U = 0f
        private const val ABOVE_FIRE_V = 0.81f
        private const val ABOVE_FIRE_SPREAD = 2f

        /** The lamp alone, at strength 1. */
        val NONE = lampOnly(1f)

        private fun lampOnly(lamp: Float) = FlameLight(0f, 0f, 0f, 1f, 0f, 0f, 0f, lamp = lamp)

        /**
         * Settings are 0..1, default mid. [lampBrightness]: lamp 0.35 to 1.25 (0.95 default);
         * [fireIntensity]: embers 0.25 to blaze 1.75. A fire turns the lamp down a little.
         */
        fun at(
            seconds: Float,
            lampBrightness: Float,
            fireplaceLit: Boolean,
            fireIntensity: Float,
            // Hearth beyond the far edge, not the left
            hearthAbove: Boolean = false,
        ): FlameLight {
            val x = lampBrightness.coerceIn(0f, 1f)
            val lamp = if (x < 0.5f) 0.35f + 1.2f * x else 0.95f + 0.6f * (x - 0.5f)
            if (!fireplaceLit) return lampOnly(lamp)
            val strength = 0.25f + 1.5f * fireIntensity.coerceIn(0f, 1f)
            return if (hearthAbove) fireplace(seconds, strength, lamp * 0.65f, ABOVE_FIRE_U, ABOVE_FIRE_V, ABOVE_FIRE_SPREAD)
            else fireplace(seconds, strength, lamp * 0.65f, FIRE_U, FIRE_V, 1f)
        }

        /**
         * A hearth fire about ([atU], [atV]): broad, redder (~1700 K), never settling. Brightness
         * wanders up to ~1/3 in surges, lulls and licks, with occasional flares; place and height
         * drift slowly plus quick erratic jumps (several a second), so shadows twitch, not sway.
         */
        private fun fireplace(t: Float, strength: Float, lamp: Float, atU: Float, atV: Float, spread: Float): FlameLight {
            val surge = noise(t * 0.9f, 0f)
            val lick = noise(t * 3.7f, 17f)
            val flutter = noise(t * 11f, 41f)
            // Occasional short flare
            val flare = (noise(t * 0.35f, 73f) - 0.55f).coerceAtLeast(0f) * 2.2f
            val flicker = (1f + 0.20f * surge + 0.11f * lick + 0.05f * flutter + 0.3f * flare).coerceIn(0.55f, 1.6f)
            val heat = (flicker - 1f) * 0.5f // brighter burns yellower
            return FlameLight(
                // Broader hearths wander further
                u = atU + spread * (0.02f * noise(t * 1.3f, 101f) + 0.012f * noise(t * 6.3f, 211f) + 0.006f * noise(t * 14.1f, 223f)),
                v = atV + 0.025f * noise(t * 1.1f, 131f) + 0.015f * noise(t * 5.7f, 227f) + 0.007f * noise(t * 13.3f, 229f),
                // Broader hearths give more light
                power = strength * flicker * spread,
                reach = 0.14f,
                r = 1f,
                g = (0.47f + 0.16f * heat).coerceIn(0.3f, 0.7f),
                b = (0.17f + 0.10f * heat).coerceIn(0.08f, 0.35f),
                lamp = lamp,
                height = 0.28f,
                steadyU = atU,
                steadyV = atV,
                steadyPower = strength * spread,
                flameHeight = 0.075f * (1f + 0.12f * lick + 0.08f * noise(t * 9.1f, 233f) + 0.15f * flare),
                spread = spread,
            )
        }

        /** Smooth noise in -1..1 over [x]; one independent strand per [seed]. */
        private fun noise(x: Float, seed: Float): Float {
            val i = floor(x)
            val f = x - i
            val s = f * f * (3f - 2f * f)
            return hash(i + seed * 57f) * (1f - s) + hash(i + 1f + seed * 57f) * s
        }

        private fun hash(n: Float): Float {
            val h = sin(n * 12.9898f) * 43758.547f
            return (h - floor(h)) * 2f - 1f
        }
    }
}
