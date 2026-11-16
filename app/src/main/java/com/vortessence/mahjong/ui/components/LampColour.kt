package com.vortessence.mahjong.ui.components

import android.graphics.RuntimeShader
import kotlin.math.ln

/**
 * The lamp's colour temperature, on the table and, a little paler so ivory reads true, the tiles.
 * [DEFAULT_KELVIN] is near candlelight (Gareth's pick, 2026-11-12, over 2700/2400/2100/1800 K).
 * Colours were tuned by eye at 2700 K; other temperatures scale each channel by the blackbody's
 * proportional change, so the lamp stays far paler than a true blackbody and only warms or cools.
 */
object LampColour {
    const val DEFAULT_KELVIN = 2000f

    /** Kelvins; previews try others. */
    @Volatile
    var kelvin = DEFAULT_KELVIN

    private const val TUNED_AT = 2700f
    private val TABLE_AT_TUNED = floatArrayOf(1f, 0.86f, 0.70f)
    private val TILES_AT_TUNED = floatArrayOf(1f, 0.89f, 0.76f)

    fun table(k: Float = kelvin) = shifted(TABLE_AT_TUNED, k)
    fun tiles(k: Float = kelvin) = shifted(TILES_AT_TUNED, k)

    // Cached per temperature, not computed per shader per frame
    private class Colours(val k: Float, val table: FloatArray, val tiles: FloatArray)
    @Volatile
    private var colours: Colours? = null

    fun setUniforms(s: RuntimeShader) {
        val k = kelvin
        val c = colours?.takeIf { it.k == k } ?: Colours(k, table(k), tiles(k)).also { colours = it }
        s.setFloatUniform("LAMP_ON_TABLE", c.table)
        s.setFloatUniform("LAMP_ON_TILES", c.tiles)
    }

    /** Menu slate: a slightly cooler [MENU_KELVIN] (Gareth's pick, 2026-11-13), or a preview's [kelvin]. */
    fun setMenuUniform(s: RuntimeShader) {
        val k = if (kelvin == DEFAULT_KELVIN) MENU_KELVIN else kelvin
        val c = menuColour?.takeIf { it.first == k } ?: (k to table(k)).also { menuColour = it }
        s.setFloatUniform("lampColour", c.second)
    }

    const val MENU_KELVIN = 2200f
    @Volatile
    private var menuColour: Pair<Float, FloatArray>? = null

    // Each channel's shortfall from white, scaled by the blackbody's at [k] relative to 2700 K
    private fun shifted(tuned: FloatArray, k: Float): FloatArray {
        val at = blackbody(k)
        val ref = blackbody(TUNED_AT)
        return FloatArray(3) { c ->
            val refGap = 1f - ref[c]
            if (refGap <= 1e-4f) tuned[c] else (1f - (1f - tuned[c]) * (1f - at[c]) / refGap).coerceIn(0f, 1f)
        }
    }

    /** sRGB blackbody at [k] K (1000-6600), red = 1: Tanner Helland's fit to Mitchell Charity's table. */
    internal fun blackbody(k: Float): FloatArray {
        val t = k.coerceIn(1000f, 6600f) / 100f
        val g = (99.4708025861f * ln(t) - 161.1195681661f) / 255f
        val b = if (t <= 19f) 0f else (138.5177312231f * ln(t - 10f) - 305.0447927307f) / 255f
        return floatArrayOf(1f, g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }
}
