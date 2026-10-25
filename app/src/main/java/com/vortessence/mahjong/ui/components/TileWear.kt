package com.vortessence.mahjong.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The look of well-used tiles: a few face-wear textures (handled grime gathered towards the edges
 * and corners, fine hairline scratches that catch the light, the odd tiny ding, a faint haze of
 * fingerprints), plus per-tile ivory ageing and a fraction-off-square placement. Everything is
 * chosen from the tile's id, so each tile always looks the same and no two neighbours match.
 *
 * The textures are generated once on a background thread; tiles draw clean until they are ready.
 */
object TileWear {
    const val VARIANTS = 8
    const val WIDTH = 180
    const val HEIGHT = 240

    @Volatile
    private var overlays: Array<ImageBitmap>? = null

    @Volatile
    private var sheens: Array<ImageBitmap>? = null
    private var started = false

    @Synchronized
    fun prepareAsync() {
        if (started) return
        started = true
        Thread({ build() }, "tile-wear").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }.start()
    }

    /** Generates the textures on the calling thread (tests and previews). */
    @Synchronized
    fun prepareNow() {
        if (overlays == null) build()
        started = true
    }

    private fun build() {
        val made = Array(VARIANTS) { generate(it) }
        sheens = Array(VARIANTS) { made[it].second }
        overlays = Array(VARIANTS) { made[it].first }
    }

    fun overlay(id: Int): ImageBitmap? = overlays?.get(mix(id) % VARIANTS)

    /**
     * The glaze's specular streaks for this tile: the lamp's reflection broken up by the same
     * ripples and scratches as its wear. Always drawn upright (the reflection belongs to the
     * lamp, not to the tile), mirrored left-right when the wear is.
     */
    fun sheen(id: Int): ImageBitmap? = sheens?.get(mix(id) % VARIANTS)

    /** 0 as generated, 1 mirrored, 2 flipped, 3 turned half round: 32 looks from 8 textures. */
    fun orientation(id: Int): Int = (mix(id) / VARIANTS) % 4

    /** How yellowed this tile's ivory is, as the alpha of a warm tint (0 = crisp, ~0.13 = aged). */
    fun ageing(id: Int): Float = (mix(id * 31 + 7) % 1000) / 1000f * 0.13f

    /** Small placement imperfection: offset as fractions of the tile width, rotation in degrees. */
    fun jitter(id: Int): Triple<Float, Float, Float> {
        val h = mix(id * 17 + 3)
        val a = (h % 1000) / 1000f - 0.5f
        val b = ((h / 1000) % 1000) / 1000f - 0.5f
        val c = ((h / 1000000) % 1000) / 1000f - 0.5f
        return Triple(a * 0.016f, b * 0.016f, c * 1.1f)
    }

    /** The face's slight unevenness: crown offset (fractions of the face) and a 0..1 wobble. */
    fun surface(id: Int): Triple<Float, Float, Float> {
        val h = mix(id * 53 + 11)
        val a = (h % 1000) / 1000f - 0.5f
        val b = ((h / 1000) % 1000) / 1000f - 0.5f
        val c = ((h / 1000000) % 1000) / 1000f
        return Triple(a * 0.16f, b * 0.12f, c)
    }

    private fun mix(id: Int): Int {
        var h = id * -0x61c88647 + 0x165667b1
        h = (h xor (h ushr 15)) * -0x7a143589
        h = h xor (h ushr 13)
        return h and 0x7fffffff
    }

    private fun hash(ix: Int, iy: Int): Float {
        var h = ix * 374761393 + iy * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0x7fffffff) / 2147483647f
    }

    private fun vnoise(x: Float, y: Float): Float {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val fx = x - ix
        val fy = y - iy
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        val a = hash(ix, iy)
        val b = hash(ix + 1, iy)
        val c = hash(ix, iy + 1)
        val d = hash(ix + 1, iy + 1)
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uy
    }

    private fun fbm(x: Float, y: Float): Float {
        var v = 0f
        var amp = 0.5f
        var px = x
        var py = y
        repeat(4) {
            v += amp * vnoise(px, py)
            px = px * 2.03f + 1.7f
            py = py * 2.03f + 9.2f
            amp *= 0.5f
        }
        return v
    }

    private fun generate(variant: Int): Pair<ImageBitmap, ImageBitmap> {
        val rnd = Random(variant * 7919 + 13)
        val w = WIDTH
        val h = HEIGHT

        // Hairline scratches: straight-ish, any direction, mostly faint, a few longer and brighter
        val n = 10 + rnd.nextInt(16)
        val sx0 = FloatArray(n); val sy0 = FloatArray(n); val sx1 = FloatArray(n); val sy1 = FloatArray(n)
        val sa = FloatArray(n); val sw = FloatArray(n)
        for (i in 0 until n) {
            val cx = rnd.nextFloat() * w
            val cy = rnd.nextFloat() * h
            val ang = rnd.nextFloat() * Math.PI.toFloat()
            val len = w * (0.08f + 0.6f * rnd.nextFloat() * rnd.nextFloat())
            val dx = kotlin.math.cos(ang) * len / 2f
            val dy = kotlin.math.sin(ang) * len / 2f
            sx0[i] = cx - dx; sy0[i] = cy - dy; sx1[i] = cx + dx; sy1[i] = cy + dy
            sa[i] = 0.12f + 0.3f * rnd.nextFloat() * rnd.nextFloat()
            sw[i] = 0.55f + 0.6f * rnd.nextFloat()
        }
        // Tiny dings, mostly near the edges where tiles knock together
        val m = 2 + rnd.nextInt(4)
        val dxs = FloatArray(m); val dys = FloatArray(m); val drs = FloatArray(m); val das = FloatArray(m)
        for (i in 0 until m) {
            val edge = rnd.nextInt(4)
            val along = rnd.nextFloat()
            val inset = 2f + rnd.nextFloat() * 9f
            when (edge) {
                0 -> { dxs[i] = along * w; dys[i] = inset }
                1 -> { dxs[i] = along * w; dys[i] = h - inset }
                2 -> { dxs[i] = inset; dys[i] = along * h }
                else -> { dxs[i] = w - inset; dys[i] = along * h }
            }
            drs[i] = 0.7f + rnd.nextFloat() * 1.3f
            das[i] = 0.22f + rnd.nextFloat() * 0.25f
        }

        val corner = w * 0.1f
        val px = IntArray(w * h)
        val sx = IntArray(w * h)
        val off = variant * 3.1f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val fx = x + 0.5f
                val fy = y + 0.5f
                // Rounded-rectangle mask matching the tile face
                val qx = max(0f, max(corner - fx, fx - (w - corner)))
                val qy = max(0f, max(corner - fy, fy - (h - corner)))
                val mask = (corner + 0.5f - sqrt(qx * qx + qy * qy)).coerceIn(0f, 1f)
                if (mask <= 0f) continue

                // Handled grime: a little everywhere, gathered towards the edges and corners
                val e = min(min(fx, w - fx), min(fy, h - fy))
                val g = fbm(fx / w * 5f + off, fy / h * 6.5f)
                var a = (0.025f + 0.09f * g * g + 0.15f * exp(-e / 7f) * (0.6f + 0.8f * g)).coerceAtMost(0.32f)
                var r = 88f; var gg = 66f; var b = 36f

                // Uneven glaze: broad, faint ripples where it pooled thicker or ran thinner
                val ripple = fbm(fx / w * 2.2f + 31f + off, fy / h * 1.6f + variant * 1.3f)
                val thin = smoothstep(0.38f, 0.22f, ripple) * 0.05f
                if (thin > 0f) {
                    val na = thin + a * (1f - thin)
                    r = (60f * thin + r * a * (1f - thin)) / na
                    gg = (50f * thin + gg * a * (1f - thin)) / na
                    b = (35f * thin + b * a * (1f - thin)) / na
                    a = na
                }
                val thick = smoothstep(0.62f, 0.8f, ripple) * 0.07f
                if (thick > 0f) {
                    val na = thick + a * (1f - thick)
                    r = (255f * thick + r * a * (1f - thick)) / na
                    gg = (252f * thick + gg * a * (1f - thick)) / na
                    b = (244f * thick + b * a * (1f - thick)) / na
                    a = na
                }

                // Faint fingerprint haze that dulls the glaze in patches
                val haze = smoothstep(0.6f, 0.78f, fbm(fx / w * 3f + 11f + off, fy / h * 3f + variant)) * 0.06f
                if (haze > 0f) {
                    val na = haze + a * (1f - haze)
                    r = (255f * haze + r * a * (1f - haze)) / na
                    gg = (255f * haze + gg * a * (1f - haze)) / na
                    b = (255f * haze + b * a * (1f - haze)) / na
                    a = na
                }

                // Scratches catch the light: thin bright lines
                var s = 0f
                for (i in 0 until n) {
                    val d = segmentDistance(fx, fy, sx0[i], sy0[i], sx1[i], sy1[i])
                    if (d < 3f) {
                        val k = d / sw[i]
                        s = max(s, sa[i] * exp(-k * k))
                    }
                }
                if (s > 0.004f) {
                    val na = s + a * (1f - s)
                    r = (255f * s + r * a * (1f - s)) / na
                    gg = (250f * s + gg * a * (1f - s)) / na
                    b = (240f * s + b * a * (1f - s)) / na
                    a = na
                }

                // Dings: small dark chips
                var dk = 0f
                for (i in 0 until m) {
                    val ddx = fx - dxs[i]
                    val ddy = fy - dys[i]
                    val d = sqrt(ddx * ddx + ddy * ddy)
                    dk = max(dk, das[i] * (1f - smoothstep(drs[i] * 0.6f, drs[i] + 0.6f, d)))
                }
                if (dk > 0.004f) {
                    val na = dk + a * (1f - dk)
                    r = (55f * dk + r * a * (1f - dk)) / na
                    gg = (42f * dk + gg * a * (1f - dk)) / na
                    b = (30f * dk + b * a * (1f - dk)) / na
                    a = na
                }

                // Specular streaks: the lamp's reflection, in a curved zone across the upper face,
                // broken into horizontal streaks by ripples in the glaze; scratches flare in it
                val u = fx / w
                val v = fy / h
                val zoneCentre = 0.2f + 0.08f * (u - 0.5f) * (u - 0.5f) * 4f
                val zone = exp(-((v - zoneCentre) * (v - zoneCentre)) / (2f * 0.1f * 0.1f))
                val streak = smoothstep(0.48f, 0.78f, fbm(u * 2.6f + off * 0.7f, v * 19f + variant * 2.3f))
                val rippleLight = 0.6f + 0.8f * smoothstep(0.35f, 0.75f, ripple)
                var spec = zone * (0.12f + 0.5f * streak) * rippleLight + zone * s * 1.8f
                // The whole face carries a faint lower sheen too, so streaks never end abruptly
                spec += 0.05f * smoothstep(0.55f, 0.85f, fbm(u * 3f + 51f, v * 14f + off)) * (1f - v)
                val specAlpha = (spec.coerceIn(0f, 0.6f) * mask * 255f).toInt()
                sx[y * w + x] = (specAlpha shl 24) or 0xFFFBF2

                val alpha = (a * mask * 255f).toInt().coerceIn(0, 255)
                px[y * w + x] = (alpha shl 24) or
                    (r.toInt().coerceIn(0, 255) shl 16) or
                    (gg.toInt().coerceIn(0, 255) shl 8) or
                    b.toInt().coerceIn(0, 255)
            }
        }
        return Pair(
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap(),
            Bitmap.createBitmap(sx, w, h, Bitmap.Config.ARGB_8888).asImageBitmap(),
        )
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax
        val vy = by - ay
        val t = (((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)).coerceIn(0f, 1f)
        val dx = px - (ax + vx * t)
        val dy = py - (ay + vy * t)
        return sqrt(dx * dx + dy * dy)
    }
}
