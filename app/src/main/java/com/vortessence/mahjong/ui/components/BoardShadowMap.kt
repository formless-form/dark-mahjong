package com.vortessence.mahjong.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.vortessence.mahjong.data.model.BoardTile
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The board's shading, worked out once each time the tiles change: a map at a quarter of the
 * flat table's resolution (see FLAME_AGSL).
 *
 * - **Red, the height map:** how tall the stack stands at each point, in layers (31 per layer, up
 *   to 8). Each tile fills its face and the side walls drawn below and to the right of it, at the
 *   height of its top. The shaders trace the fire's long shadows through it per pixel, every
 *   frame, so a stack's shadow stops at a taller one, falls across lower ones, softens as it
 *   reaches away (the fire is broad) and sways with the flames.
 * - **Green, ambient occlusion on the table:** how hemmed in each point of the table is by the
 *   tiles around it, which darkens the table at the board's edge and in the gaps. A cheap stand-in,
 *   worked out here rather than per pixel, and used by the table only (the tiles' own walls
 *   darken towards their foot instead), so no tile picks up a rim from it.
 *
 * Building one takes some tens of milliseconds, so the board makes it off the main thread.
 */
class BoardShadowMap private constructor(
    private val shader: BitmapShader,
    // One layer's height and the tallest stack's, in table heights (as the shaders' q)
    private val layer: Float,
    private val tallest: Float,
) {

    /** [fireShadows]: trace the fire's long shadows as well as the table's ambient occlusion. */
    fun setUniforms(s: RuntimeShader, fireShadows: Boolean) {
        s.setInputShader("shadeMap", shader)
        s.setFloatUniform("shade", if (fireShadows) 1f else 0f, SCALE, layer, tallest)
        s.setFloatUniform("aoStrength", AMBIENT_OCCLUSION)
    }

    companion object {
        private const val SCALE = 0.25f
        private const val PER_LAYER = 31
        private const val AMBIENT_OCCLUSION = 0.75f

        // A tile stands about this much of its width tall
        private const val TILE_HEIGHT = 0.15f

        private val none by lazy {
            BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        /** Leaves the shaders' light unshaded. */
        fun setNone(s: RuntimeShader) {
            s.setInputShader("shadeMap", none)
            s.setFloatUniform("shade", 0f, SCALE, 1f, 1f)
            s.setFloatUniform("aoStrength", 0f)
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

            // Ambient occlusion for the table: two rings of neighbours around each point, each
            // darkening by how steeply it rises above the table
            val ringX = FloatArray(8) { cos(it * 0.785398f + 0.3f) }
            val ringY = FloatArray(8) { sin(it * 0.785398f + 0.3f) }
            val pixels = IntArray(mw * mh)
            for (j in 0 until mh) for (i in 0 until mw) {
                val px = i + 0.5f
                val py = j + 0.5f
                var occ = 0f
                for (d in 0 until 8) for (ring in 0..1) {
                    val r = tileCells * (if (ring == 0) 0.07f else 0.22f)
                    val rise = heightAt(px + ringX[d] * r, py + ringY[d] * r)
                    occ += (rise / (r * cell + 0.4f * layer)).coerceIn(0f, 1f)
                }
                val k = j * mw + i
                val layers = (height[k] / layer + 0.5f).toInt()
                pixels[k] = android.graphics.Color.rgb((layers * PER_LAYER).coerceAtMost(255), (occ / 16f * 255).toInt(), 0)
            }
            val bitmap = Bitmap.createBitmap(pixels, mw, mh, Bitmap.Config.ARGB_8888)
            val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
            return BoardShadowMap(shader, layer, tallest)
        }
    }
}
