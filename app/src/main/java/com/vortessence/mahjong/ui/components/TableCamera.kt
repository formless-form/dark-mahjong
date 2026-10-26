package com.vortessence.mahjong.ui.components

import android.graphics.Matrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * The table is seen through a camera tilted slightly away from straight overhead, so the far
 * (top) edge recedes and the near edge comes closer. Everything on the table is laid out flat in
 * "plane" pixel coordinates (as if seen from directly above) and then projected to the screen
 * through one homography. The table shader uses the inverse of exactly the same projection, so
 * tiles and tabletop always agree, and taps are mapped back through it.
 *
 * Plane coordinates in shader units: u right, v away from the viewer, both in screen heights,
 * with (0, 0) at the screen centre.
 */
object TableCamera {
    /** Tilt away from overhead. Small enough that upper layers never hide lower tiles' faces. */
    const val TILT_DEGREES = 20f

    /** Camera distance in screen heights: further means milder perspective. */
    const val DISTANCE = 2.2f

    /** The furthest the player can pinch in, as a multiple of the fitted board. */
    const val MAX_ZOOM = 3f

    private val tilt = Math.toRadians(TILT_DEGREES.toDouble()).toFloat()

    /** Projects plane coordinates (u, v) to screen pixels for a canvas of size [w] x [h]. */
    fun planeToScreen(u: Float, v: Float, w: Float, h: Float): Pair<Float, Float> {
        val z = DISTANCE + v * sin(tilt)
        val sx = u * DISTANCE / z
        val sy = v * cos(tilt) * DISTANCE / z
        return Pair(w / 2f + sx * h, h / 2f - sy * h)
    }

    /**
     * Matrix mapping flat plane pixels (an untilted w x h canvas) onto the screen. Built from the
     * four projected corners, which is exact because a tilted plane maps by a homography.
     */
    fun planeMatrix(w: Float, h: Float): Matrix {
        val src = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val dst = FloatArray(8)
        for (i in 0 until 4) {
            val px = src[i * 2]
            val py = src[i * 2 + 1]
            val (sx, sy) = planeToScreen((px - w / 2f) / h, (h / 2f - py) / h, w, h)
            dst[i * 2] = sx
            dst[i * 2 + 1] = sy
        }
        return Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
    }

    /** How much wider the near edge is drawn than the layout: leave this much room when fitting. */
    fun nearEdgeScale(): Float = DISTANCE / (DISTANCE - 0.5f * sin(tilt))

}
