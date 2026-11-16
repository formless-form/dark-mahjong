package com.vortessence.mahjong.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.vortessence.mahjong.data.model.BoardTile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The board's shading, rebuilt off the main thread (tens of ms) when tiles change; see FLAME_AGSL.
 *
 * - **Height map:** stack height in layers (31 per layer, up to 8) at full table resolution, each
 *   tile drawn as TileRenderer draws it (rounded corners, walls swept down-right). The fire's
 *   long shadows are traced through it per pixel each frame, so they follow curved edges.
 * - **Table ambient occlusion:** darkens the table at the board's edge and in gaps. Quarter
 *   resolution, table only (tiles get their own contact shadows), so no tile picks up a rim.
 * - **Surface map** (sets with ImperialImages surface maps): face normals at half resolution, so
 *   the stone's relief turns to the flickering fire. Fire only, so never in low-performance mode.
 *
 * Board scaling is only a scale and shift, so [placed] maps the current board onto the maps.
 */
class BoardShadowMap private constructor(
    private val heights: BitmapShader,
    private val occlusion: BitmapShader,
    // Null for a set with no surface maps
    private val relief: BitmapShader?,
    // Layout when built
    private val built: BoardTransform,
    // In the tallest stack
    private val layers: Int,
    // Tiles' extent plus margin (left, top, right, bottom, map pixels); left > right when empty.
    // The shaders look for shadows only within it
    private val box: FloatArray,
    val tiles: List<BoardTile>,
) {

    /** The map placed under the board as laid out [now], on a table [tableHeight] tall. */
    fun placed(now: BoardTransform, tableHeight: Float) = Placed(now, tableHeight)

    inner class Placed(now: BoardTransform, tableHeight: Float) {
        // Table pixels now to map pixels
        private val scale = built.unitX / now.unitX
        private val offsetX = built.startX - now.startX * scale
        private val offsetY = built.startY - now.startY * scale
        // One layer in table heights (as the shaders' q)
        private val layer = now.tileWidth * TILE_HEIGHT / tableHeight

        /** [fireShadows]: trace the fire's long shadows as well as ambient occlusion. */
        fun setUniforms(s: RuntimeShader, fireShadows: Boolean) {
            s.setInputShader("heightMap", heights)
            s.setInputShader("aoMap", occlusion)
            s.setInputShader("tileReliefMap", relief ?: none)
            s.setFloatUniform("tileRelief", if (relief != null && TableSurfaceRenderer.tileRelief) 1f else 0f, RELIEF_SCALE)
            s.setFloatUniform("mapXform", scale, scale, offsetX, offsetY)
            s.setFloatUniform("shade", if (fireShadows) 1f else 0f, AO_SCALE, layer, layers * layer)
            s.setFloatUniform("shadowSteps", FireShadows.steps)
            s.setFloatUniform("aoStrength", AMBIENT_OCCLUSION)
            s.setFloatUniform("boardBox", box)
        }
    }

    companion object {
        private const val AO_SCALE = 0.25f
        // Per height-map pixel: light changes broadly across a face
        private const val RELIEF_SCALE = 0.5f
        private const val PER_LAYER = 31
        private const val AMBIENT_OCCLUSION = 0.8f

        // Tile height as a fraction of its width
        private const val TILE_HEIGHT = 0.15f

        private val none by lazy {
            BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        /** Leaves the shaders' light unshaded. */
        fun setNone(s: RuntimeShader) {
            s.setInputShader("heightMap", none)
            s.setInputShader("aoMap", none)
            s.setInputShader("tileReliefMap", none)
            s.setFloatUniform("tileRelief", 0f, RELIEF_SCALE)
            s.setFloatUniform("mapXform", 1f, 1f, 0f, 0f)
            s.setFloatUniform("shade", 0f, AO_SCALE, 1f, 1f)
            s.setFloatUniform("shadowSteps", FireShadows.FULL)
            s.setFloatUniform("aoStrength", 0f)
            s.setFloatUniform("boardBox", 1f, 1f, 0f, 0f)
        }

        /** [relief]: the surface map too, where the set has one (fire only). */
        fun build(tiles: List<BoardTile>, transform: BoardTransform, w: Float, h: Float, relief: Boolean = false): BoardShadowMap? {
            if (w <= 0f || h <= 0f) return null
            val standing = tiles.filter { !it.isRemoved }.sortedBy { it.z }
            val layer = transform.tileWidth * TILE_HEIGHT / h
            val box = bounds(standing, transform)
            return BoardShadowMap(
                heightMap(standing, transform, w, h),
                occlusionMap(standing, transform, w, h, layer, box),
                if (relief) surfaceMap(standing, transform, w, h) else null,
                transform,
                (standing.maxOfOrNull { it.z } ?: 0) + 1,
                box,
                tiles,
            )
        }

        /** Standing tiles' extent in map pixels, with room for the map's smoothing. */
        private fun bounds(standing: List<BoardTile>, transform: BoardTransform): FloatArray {
            if (standing.isEmpty()) return floatArrayOf(1f, 1f, 0f, 0f)
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            for (tile in standing) {
                val x = transform.tilePixelX(tile.x, tile.z)
                val y = transform.tilePixelY(tile.y, tile.z)
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + transform.tileWidth + transform.depthX)
                bottom = maxOf(bottom, y + transform.tileHeight + transform.depthY)
            }
            val margin = transform.tileWidth * 0.04f + 3f
            return floatArrayOf(left - margin, top - margin, right + margin, bottom + margin)
        }

        /**
         * Each tile's body map (its own look, as the atlas stamps it) with its engraving's over it,
         * in draw order so each point holds the top tile's. Null for other sets or until loaded.
         */
        private fun surfaceMap(standing: List<BoardTile>, transform: BoardTransform, w: Float, h: Float): BitmapShader? {
            if (!TileKits.current.stone) return null
            val images = ImperialImages.ready ?: return null
            val bodies = images.bodySurfaces ?: return null
            val engravings = images.engravingSurfaces ?: return null
            val bitmap = Bitmap.createBitmap((w * RELIEF_SCALE).toInt().coerceAtLeast(1), (h * RELIEF_SCALE).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(RELIEF_SCALE, RELIEF_SCALE)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val place = android.graphics.RectF()
            val k = transform.tileWidth / images.surfaceWidth
            for (tile in standing.sortedWith(TileDrawOrder)) {
                val x = transform.tilePixelX(tile.x, tile.z)
                val y = transform.tilePixelY(tile.y, tile.z)
                val body = bodies[TileWear.look(tile.id) % bodies.size]
                place.set(x, y, x + body.width * k, y + body.height * k)
                canvas.drawBitmap(body, null, place, paint)
                engravings[tile.tile]?.let { canvas.drawBitmap(it, null, place, paint) }
            }
            return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
        }

        /** Full resolution, each tile drawn exactly as on screen. */
        private fun heightMap(standing: List<BoardTile>, transform: BoardTransform, w: Float, h: Float): BitmapShader {
            val bitmap = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ALPHA_8)
            val canvas = Canvas(bitmap)
            // SRC, lowest first, so each point holds the top tile's height
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
            val tw = transform.tileWidth
            val th = transform.tileHeight
            val depthX = tw * BoardTransform.LAYER_SHIFT_FACTOR_X
            val depthY = th * BoardTransform.LAYER_SHIFT_FACTOR_Y
            val corner = tw * 0.085f
            for (tile in standing) {
                val x = transform.tilePixelX(tile.x, tile.z)
                val y = transform.tilePixelY(tile.y, tile.z)
                paint.alpha = ((tile.z + 1) * PER_LAYER).coerceAtMost(255)
                // Face, then walls swept to full depth
                for (k in 0..3) {
                    val dx = depthX * k / 3f
                    val dy = depthY * k / 3f
                    canvas.drawRoundRect(x + dx, y + dy, x + dx + tw, y + dy + th, corner, corner, paint)
                }
            }
            return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
        }

        /**
         * Quarter resolution: two rings of neighbours per point, each darkening by how steeply it
         * rises. Only around the board ([box]); nothing rises further out.
         */
        private fun occlusionMap(
            standing: List<BoardTile>,
            transform: BoardTransform,
            w: Float,
            h: Float,
            layer: Float,
            box: FloatArray,
        ): BitmapShader {
            val mw = (w * AO_SCALE).toInt().coerceAtLeast(2)
            val mh = (h * AO_SCALE).toInt().coerceAtLeast(2)
            val cell = 1f / (h * AO_SCALE) // table heights per cell
            val tileCells = transform.tileWidth * AO_SCALE
            val depthX = transform.tileWidth * BoardTransform.LAYER_SHIFT_FACTOR_X
            val depthY = transform.tileHeight * BoardTransform.LAYER_SHIFT_FACTOR_Y
            val radius = transform.tileWidth * 0.085f * AO_SCALE

            // Coarse height map, rounded tiles (no nudges: the occlusion is soft anyway)
            val height = FloatArray(mw * mh)
            for (tile in standing) {
                val top = (tile.z + 1) * layer
                val x = transform.tilePixelX(tile.x, tile.z)
                val y = transform.tilePixelY(tile.y, tile.z)
                val left = x * AO_SCALE
                val right = (x + transform.tileWidth + depthX) * AO_SCALE
                val upper = y * AO_SCALE
                val lower = (y + transform.tileHeight + depthY) * AO_SCALE
                val cx = (left + right) / 2f
                val cy = (upper + lower) / 2f
                val innerX = (right - left) / 2f - radius
                val innerY = (lower - upper) / 2f - radius
                for (j in upper.toInt().coerceIn(0, mh - 1)..lower.toInt().coerceIn(0, mh - 1)) {
                    for (i in left.toInt().coerceIn(0, mw - 1)..right.toInt().coerceIn(0, mw - 1)) {
                        val dx = maxOf(abs(i + 0.5f - cx) - innerX, 0f)
                        val dy = maxOf(abs(j + 0.5f - cy) - innerY, 0f)
                        if (dx * dx + dy * dy > radius * radius) continue
                        val k = j * mw + i
                        if (height[k] < top) height[k] = top
                    }
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

            val ringX = FloatArray(8) { cos(it * 0.785398f + 0.3f) }
            val ringY = FloatArray(8) { sin(it * 0.785398f + 0.3f) }
            val pixels = IntArray(mw * mh)
            // Board bounds in cells, plus the outer ring's reach
            val ringReach = tileCells * 0.22f + 2f
            val i0 = (box[0] * AO_SCALE - ringReach).toInt().coerceIn(0, mw)
            val i1 = (box[2] * AO_SCALE + ringReach).toInt().coerceIn(0, mw - 1)
            val j0 = (box[1] * AO_SCALE - ringReach).toInt().coerceIn(0, mh)
            val j1 = (box[3] * AO_SCALE + ringReach).toInt().coerceIn(0, mh - 1)
            for (j in j0..j1) for (i in i0..i1) {
                val px = i + 0.5f
                val py = j + 0.5f
                var occ = 0f
                for (d in 0 until 8) for (ring in 0..1) {
                    val r = tileCells * (if (ring == 0) 0.07f else 0.22f)
                    val rise = heightAt(px + ringX[d] * r, py + ringY[d] * r)
                    occ += (rise / (r * cell + 0.4f * layer)).coerceIn(0f, 1f)
                }
                pixels[j * mw + i] = android.graphics.Color.rgb(0, (occ / 16f * 255).toInt(), 0)
            }
            val bitmap = Bitmap.createBitmap(pixels, mw, mh, Bitmap.Config.ARGB_8888)
            return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
        }
    }
}
