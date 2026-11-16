package com.vortessence.mahjong.ui.components

import android.graphics.Matrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * A camera tilted slightly from overhead. The table is laid out flat in "plane" coordinates and
 * projected to the screen by one homography; the table shader and tap mapping use its inverse, so
 * tiles and tabletop always agree.
 *
 * Plane shader units: u right, v away from the viewer, in screen heights, origin at screen centre.
 */
object TableCamera {
    /** Small enough that upper layers never hide lower tiles' faces. */
    const val TILT_DEGREES = 20f

    /** In screen heights: further means milder perspective. */
    const val DISTANCE = 2.2f

    /** Pinch-zoom limit, as a multiple of the fitted board. */
    const val MAX_ZOOM = 3f

    private val tilt = Math.toRadians(TILT_DEGREES.toDouble()).toFloat()

    /** Plane (u, v) to screen pixels on a [w] x [h] canvas. */
    fun planeToScreen(u: Float, v: Float, w: Float, h: Float): Pair<Float, Float> {
        val z = DISTANCE + v * sin(tilt)
        val sx = u * DISTANCE / z
        val sy = v * cos(tilt) * DISTANCE / z
        return Pair(w / 2f + sx * h, h / 2f - sy * h)
    }

    /** Flat plane pixels (an untilted w x h canvas) to screen; exact from four corners (homography). */
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

    /** Near-edge widening over the layout: room to leave when fitting. */
    fun nearEdgeScale(): Float = DISTANCE / (DISTANCE - 0.5f * sin(tilt))

}
