package com.vortessence.mahjong

import com.vortessence.mahjong.ui.components.TableCamera
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The tabletop must sit in exactly the same perspective as the tiles. Tiles are projected by
 * [TableCamera.planeMatrix]; the table shader maps each screen pixel back onto the table with its
 * own copy of the inverse maths (mirrored in [shaderScreenToPlane]). Both must agree everywhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TableCameraTest {
    private val w = 960f
    private val h = 1900f

    /** The table shader's screen -> table mapping, transcribed from TableSurfaceRenderer's AGSL. */
    private fun shaderScreenToPlane(px: Float, py: Float): Pair<Float, Float> {
        val tilt = Math.toRadians(TableCamera.TILT_DEGREES.toDouble()).toFloat()
        val d = TableCamera.DISTANCE
        val sx = (px - 0.5f * w) / h
        val sy = -(py - 0.5f * h) / h
        val v = sy * d / (cos(tilt) * d - sy * sin(tilt))
        val z = d + v * sin(tilt)
        val u = sx * z / d
        return Pair(u, v)
    }

    @Test
    fun shaderMappingIsTheExactInverseOfTheTileProjection() {
        val rnd = Random(1)
        repeat(500) {
            val px = rnd.nextFloat() * w
            val py = rnd.nextFloat() * h
            val (u, v) = shaderScreenToPlane(px, py)
            val (bx, by) = TableCamera.planeToScreen(u, v, w, h)
            assertEquals(px, bx, 0.01f)
            assertEquals(py, by, 0.01f)
        }
    }

    @Test
    fun tileMatrixMatchesTheCameraEverywhere() {
        val m = TableCamera.planeMatrix(w, h)
        val rnd = Random(2)
        repeat(500) {
            // A point in the flat table layout (plane pixels), anywhere on or around the board
            val x = -0.2f * w + rnd.nextFloat() * 1.4f * w
            val y = -0.2f * h + rnd.nextFloat() * 1.4f * h
            val p = floatArrayOf(x, y)
            m.mapPoints(p)
            val (ex, ey) = TableCamera.planeToScreen((x - w / 2f) / h, (h / 2f - y) / h, w, h)
            assertEquals(ex, p[0], 0.02f)
            assertEquals(ey, p[1], 0.02f)
        }
    }
}
