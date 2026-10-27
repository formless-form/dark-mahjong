package com.vortessence.mahjong.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.vortessence.mahjong.data.model.BoardTile
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The board's shading, worked out once each time the tiles change rather than every frame: a
 * small light map at an eighth of the flat table's resolution, read by the table and tile
 * shaders with one lookup per pixel (see FLAME_AGSL).
 *
 * - **Red, ambient occlusion:** how hemmed in each point is by taller tiles around it, which
 *   darkens the foot of a taller stack and the table around the board.
 * - **Green, the fire's long shadows:** how much of the fire reaches each point. The fire burns
 *   low beside the table, so its light skims across the board and a stack throws a shadow several
 *   tiles long. Traced from each point towards the fire as it burns on average, through a height
 *   map of the board, so a shadow stops at a taller stack, falls across lower ones and softens as
 *   it reaches away (the fire is broad). The shaders shift it a little as the flames move.
 *
 * Building one takes some tens of milliseconds, so the board makes it off the main thread.
 */
class BoardShadowMap private constructor(private val shader: BitmapShader) {

    /** [fireShadows]: the fire's long shadows as well as the ambient occlusion. */
    fun setUniforms(s: RuntimeShader, fireShadows: Boolean) {
        s.setInputShader("lightMap", shader)
        s.setFloatUniform("shade", if (fireShadows) 1f else 0f, SCALE, AMBIENT_OCCLUSION, SWAY)
    }

    companion object {
        private const val SCALE = 0.125f
        private const val AMBIENT_OCCLUSION = 0.75f
        // How far the shadows move for each step the flames take
        private const val SWAY = 0.4f

        // A tile stands about this much of its width tall
        private const val TILE_HEIGHT = 0.15f
        // How high the flames burn above the table, in table heights
        private const val FIRE_HEIGHT = 0.075f
        private const val SHADOW_STEPS = 40

        private val none by lazy {
            BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        /** Leaves the shaders' light unshaded. */
        fun setNone(s: RuntimeShader) {
            s.setInputShader("lightMap", none)
            s.setFloatUniform("shade", 0f, SCALE, 0f, 0f)
        }

        fun build(tiles: List<BoardTile>, transform: BoardTransform, w: Float, h: Float): BoardShadowMap? {
            if (w <= 0f || h <= 0f) return null
            val mw = (w * SCALE).toInt().coerceAtLeast(2)
            val mh = (h * SCALE).toInt().coerceAtLeast(2)
            // Table heights per map cell, one layer's height, and a tile's width in cells
            val cell = 1f / (h * SCALE)
            val layer = transform.tileWidth * TILE_HEIGHT / h
            val tileCells = transform.tileWidth * SCALE

            // The height map: each tile fills its face and the side walls drawn below and to the
            // right of it, at the height of its top
            val height = FloatArray(mw * mh)
            val depthX = transform.tileWidth * BoardTransform.LAYER_SHIFT_FACTOR_X
            val depthY = transform.tileHeight * BoardTransform.LAYER_SHIFT_FACTOR_Y
            var tallest = 0f
            for (tile in tiles) {
                if (tile.isRemoved) continue
                val top = (tile.z + 1) * layer
                tallest = maxOf(tallest, top)
                val x = transform.tilePixelX(tile.x, tile.z)
                val y = transform.tilePixelY(tile.y, tile.z)
                val x0 = (x * SCALE).toInt().coerceIn(0, mw - 1)
                val x1 = ((x + transform.tileWidth + depthX) * SCALE).toInt().coerceIn(0, mw - 1)
                val y0 = (y * SCALE).toInt().coerceIn(0, mh - 1)
                val y1 = ((y + transform.tileHeight + depthY) * SCALE).toInt().coerceIn(0, mh - 1)
                for (j in y0..y1) for (i in x0..x1) {
                    val k = j * mw + i
                    if (height[k] < top) height[k] = top
                }
            }

            fun heightAt(fx: Float, fy: Float): Float {
                val x = fx.coerceIn(0f, mw - 1.001f)
                val y = fy.coerceIn(0f, mh - 1.001f)
                val i = floor(x).toInt()
                val j = floor(y).toInt()
                val tx = x - i
                val ty = y - j
                val a = height[j * mw + i] * (1 - tx) + height[j * mw + i + 1] * tx
                val b = height[(j + 1) * mw + i] * (1 - tx) + height[(j + 1) * mw + i + 1] * tx
                return a * (1 - ty) + b * ty
            }

            // The fire, as it burns on average, in map cells
            val fireX = (w / 2f + FlameLight.FIRE_U * h) * SCALE
            val fireY = (h / 2f - FlameLight.FIRE_V * h) * SCALE
            val ringX = FloatArray(8) { cos(it * 0.785398f + 0.3f) }
            val ringY = FloatArray(8) { sin(it * 0.785398f + 0.3f) }
            val pixels = IntArray(mw * mh)
            for (j in 0 until mh) for (i in 0 until mw) {
                val px = i + 0.5f
                val py = j + 0.5f
                val h0 = heightAt(px, py)

                // Ambient occlusion: two rings of neighbours, each darkening by how steeply it
                // rises above this point
                var occ = 0f
                for (d in 0 until 8) for (ring in 0..1) {
                    val r = tileCells * (if (ring == 0) 0.07f else 0.22f)
                    val rise = heightAt(px + ringX[d] * r, py + ringY[d] * r) - h0
                    occ += (rise / (r * cell + 0.4f * layer)).coerceIn(0f, 1f)
                }
                occ /= 16f

                // The fire's shadow: march towards the flames while the ray stays below the
                // tallest stack, keeping the deepest occlusion, softened as the ray reaches away
                var lit = 1f
                if (h0 < tallest) {
                    val dx = fireX - px
                    val dy = fireY - py
                    val dist = sqrt(dx * dx + dy * dy) * cell
                    val rise = (FIRE_HEIGHT - h0) / dist
                    val reach = minOf(dist, (tallest - h0) / maxOf(rise, 1e-4f))
                    val ux = dx * cell / dist
                    val uy = dy * cell / dist
                    var occluded = 0f
                    for (step in 0 until SHADOW_STEPS) {
                        val s = reach * (step + 0.5f) / SHADOW_STEPS + 0.6f * layer
                        // Less a little, so a tile's own softened edge never shades it
                        val above = heightAt(px + ux * s / cell, py + uy * s / cell) - (h0 + s * rise) - 0.35f * layer
                        val soft = 0.8f * layer + 0.12f * s
                        occluded = maxOf(occluded, (above / soft).coerceIn(0f, 1f))
                    }
                    lit = 1f - occluded
                }
                pixels[j * mw + i] = android.graphics.Color.rgb((occ * 255).toInt(), (lit * 255).toInt(), 0)
            }
            val bitmap = Bitmap.createBitmap(pixels, mw, mh, Bitmap.Config.ARGB_8888)
            val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
            return BoardShadowMap(shader)
        }
    }
}
