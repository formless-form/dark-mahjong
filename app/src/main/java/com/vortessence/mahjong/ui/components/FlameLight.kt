package com.vortessence.mahjong.ui.components

import android.graphics.RuntimeShader
import com.vortessence.mahjong.data.model.TableLighting
import kotlin.math.floor
import kotlin.math.sin

/**
 * Warm light from a flame out of view, added to the lamp: where it stands on the table plane
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
) {
    fun setUniforms(s: RuntimeShader) {
        s.setFloatUniform("flame", u, v, power, reach)
        s.setFloatUniform("flameColour", r, g, b)
        s.setFloatUniform("lampLevel", lamp)
    }

    companion object {
        /** The lamp alone. */
        val NONE = FlameLight(0f, 0f, 0f, 1f, 0f, 0f, 0f, lamp = 1f)

        /**
         * The flame for [lighting] at [seconds], or null for the lamp alone. [fireIntensity]
         * (0..1, the player's setting) runs the fireplace from embers (a quarter of its natural
         * strength, at 0) to a roaring blaze (one and three quarters, at 1).
         */
        fun at(lighting: TableLighting, seconds: Float, fireIntensity: Float = 0.5f): FlameLight? = when (lighting) {
            TableLighting.LAMP -> null
            TableLighting.CANDLE -> candle(seconds)
            TableLighting.FIREPLACE -> fireplace(seconds, 0.25f + 1.5f * fireIntensity.coerceIn(0f, 1f))
        }

        /**
         * A candle on the table just out of view past the top-left of the board, about 1900 K.
         * It breathes rather than strobes: a slow sway and a quicker shiver, a few percent each.
         */
        private fun candle(t: Float): FlameLight {
            val sway = 0.5f * sin(t * 1.7f) + 0.5f * sin(t * 2.9f)
            val shiver = sin(t * 11.3f) * sin(t * 17.9f)
            val flicker = 1f + 0.035f * sway + 0.025f * shiver
            return FlameLight(-0.30f, 0.56f, 0.7f * flicker, 0.09f, 1f, 0.62f, 0.30f, lamp = 0.75f)
        }

        /**
         * A fire in a hearth off to the left of the table: a broad, redder source (about 1700 K)
         * that never settles. Its brightness wanders by up to about a third, in random surges
         * and lulls with quick licks of flame on top, and now and then a log catches and it flares.
         * As the flames move, so does the light, a little; brighter flames burn yellower.
         */
        private fun fireplace(t: Float, strength: Float): FlameLight {
            val surge = noise(t * 0.9f, 0f)
            val lick = noise(t * 3.7f, 17f)
            val flutter = noise(t * 11f, 41f)
            // A flare: an occasional, short brightening
            val flare = (noise(t * 0.35f, 73f) - 0.55f).coerceAtLeast(0f) * 2.2f
            val flicker = (1f + 0.20f * surge + 0.11f * lick + 0.05f * flutter + 0.3f * flare).coerceIn(0.55f, 1.6f)
            val heat = (flicker - 1f) * 0.5f // brighter burns yellower, lulls glow redder
            return FlameLight(
                u = -0.50f + 0.025f * noise(t * 1.3f, 101f),
                v = 0.10f + 0.035f * noise(t * 1.1f, 131f),
                power = strength * flicker,
                reach = 0.14f,
                r = 1f,
                g = (0.47f + 0.16f * heat).coerceIn(0.3f, 0.7f),
                b = (0.17f + 0.10f * heat).coerceIn(0.08f, 0.35f),
                lamp = 0.65f,
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
