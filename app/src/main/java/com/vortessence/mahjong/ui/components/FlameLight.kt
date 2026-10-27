package com.vortessence.mahjong.ui.components

import android.graphics.RuntimeShader
import kotlin.math.floor
import kotlin.math.sin

/**
 * How the table is lit this frame: the lamp overhead at strength [lamp], plus warm light from a
 * fire out of view (none when [power] is 0): where it stands on the table plane
 * ([u], [v], in table heights, v up, as the table shader), its [power], its [reach] (the squared
 * distance at which it falls to half), its colour, and how far the [lamp] is turned down beside
 * it. Recomputed every frame, since flames never hold still.
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
    // How high the light stands above the table, in table heights: sets how long shadows reach
    val height: Float = 0.2f,
    // The fire as it burns on average, without its flicker: for anything that must hold still
    val steadyU: Float = u,
    val steadyV: Float = v,
    val steadyPower: Float = power,
    // How high the flames stand above the table this frame, in table heights: the long shadows
    // are traced from here, so they stretch and shrink as the flames leap and drop
    val flameHeight: Float = 0.075f,
) {
    fun setUniforms(s: RuntimeShader) {
        s.setFloatUniform("flame", u, v, power, reach)
        s.setFloatUniform("flameColour", r, g, b)
        s.setFloatUniform("lampLevel", lamp)
        s.setFloatUniform("flameHeight", flameHeight)
    }

    companion object {
        /** Where the fireplace's fire burns on average, in table heights (as the shaders' q). */
        const val FIRE_U = -0.50f
        const val FIRE_V = 0.10f

        /** The lamp alone, at its natural strength. */
        val NONE = lampOnly(1f)

        private fun lampOnly(lamp: Float) = FlameLight(0f, 0f, 0f, 1f, 0f, 0f, 0f, lamp = lamp)

        /**
         * The light at [seconds] for the player's settings, each 0..1 with its default in the
         * middle. [lampBrightness] runs the lamp from about a third of its original strength
         * to a quarter brighter than it, 5% dimmer than it by default; [fireIntensity] runs a lit fire from embers (a quarter) to a roaring
         * blaze (one and three quarters). Beside a fire the lamp is turned down a little.
         */
        fun at(seconds: Float, lampBrightness: Float, fireplaceLit: Boolean, fireIntensity: Float): FlameLight {
            val x = lampBrightness.coerceIn(0f, 1f)
            val lamp = if (x < 0.5f) 0.35f + 1.2f * x else 0.95f + 0.6f * (x - 0.5f)
            if (!fireplaceLit) return lampOnly(lamp)
            return fireplace(seconds, 0.25f + 1.5f * fireIntensity.coerceIn(0f, 1f), lamp * 0.65f)
        }

        /**
         * A fire in a hearth off to the left of the table: a broad, redder source (about 1700 K)
         * that never settles. Its brightness wanders by up to about a third, in random surges
         * and lulls with quick licks of flame on top, and now and then a log catches and it flares.
         * As the flames move, so does the light: a slow drift, and on top of it the quick, erratic
         * jumps of real flames (several a second), in both place and height. A flare lifts the
         * flames, so the shadows they cast twitch, stretch and shrink rather than sway. Brighter
         * flames burn yellower.
         */
        private fun fireplace(t: Float, strength: Float, lamp: Float): FlameLight {
            val surge = noise(t * 0.9f, 0f)
            val lick = noise(t * 3.7f, 17f)
            val flutter = noise(t * 11f, 41f)
            // A flare: an occasional, short brightening
            val flare = (noise(t * 0.35f, 73f) - 0.55f).coerceAtLeast(0f) * 2.2f
            val flicker = (1f + 0.20f * surge + 0.11f * lick + 0.05f * flutter + 0.3f * flare).coerceIn(0.55f, 1.6f)
            val heat = (flicker - 1f) * 0.5f // brighter burns yellower, lulls glow redder
            return FlameLight(
                u = FIRE_U + 0.02f * noise(t * 1.3f, 101f) + 0.012f * noise(t * 6.3f, 211f) + 0.006f * noise(t * 14.1f, 223f),
                v = FIRE_V + 0.025f * noise(t * 1.1f, 131f) + 0.015f * noise(t * 5.7f, 227f) + 0.007f * noise(t * 13.3f, 229f),
                power = strength * flicker,
                reach = 0.14f,
                r = 1f,
                g = (0.47f + 0.16f * heat).coerceIn(0.3f, 0.7f),
                b = (0.17f + 0.10f * heat).coerceIn(0.08f, 0.35f),
                lamp = lamp,
                height = 0.28f,
                steadyU = FIRE_U,
                steadyV = FIRE_V,
                steadyPower = strength,
                flameHeight = 0.075f * (1f + 0.12f * lick + 0.08f * noise(t * 9.1f, 233f) + 0.15f * flare),
            )
        }

        /** Smooth random wandering in -1..1 over time [x], one independent strand per [seed]. */
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
