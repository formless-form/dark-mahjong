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
 * Well-used tiles: a few wear textures (edge grime, hairline scratches, dings, fingerprint haze)
 * and ivory ageing, chosen by tile id so each tile is stable and neighbours differ. Generated once
 * on a background thread; tiles draw clean until then.
 */
object TileWear {
    const val VARIANTS = 8
    const val LOOKS = VARIANTS * 4
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

    /** On the calling thread (tests and previews). */
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

    val ready: Boolean get() = overlays != null

    /** Texture (look % VARIANTS) and orientation (look / VARIANTS): all of a worn face follows from it. */
    fun look(id: Int): Int = mix(id) % LOOKS

    fun overlay(look: Int): ImageBitmap? = overlays?.get(look % VARIANTS)

    /**
     * The lamp's reflection, broken by the wear's ripples and scratches. Always upright (it belongs
     * to the lamp), mirrored when the wear is.
     */
    fun sheen(look: Int): ImageBitmap? = sheens?.get(look % VARIANTS)

    /** 0 as generated, 1 mirrored, 2 flipped, 3 turned half round: 32 looks from 8 textures. */
    fun orientation(look: Int): Int = look / VARIANTS

    /**
     * Warm tint alpha (0 crisp, ~0.13 aged). The same for every tile: a set ages together, and
     * differing tones side by side read as light or height differences.
     */
    @Suppress("UNUSED_PARAMETER")
    fun ageing(id: Int): Float = 0.065f

    /** Glaze unevenness (0..1), bending the lamp's reflection. */
    fun wobble(look: Int): Float = (mix(look * 53 + 11) % 1000) / 1000f

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

        // Hairline scratches: straight-ish, mostly faint, a few longer and brighter
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
        // Dings, mostly near the edges where tiles knock
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
                // Face-shaped rounded mask
                val qx = max(0f, max(corner - fx, fx - (w - corner)))
                val qy = max(0f, max(corner - fy, fy - (h - corner)))
                val mask = (corner + 0.5f - sqrt(qx * qx + qy * qy)).coerceIn(0f, 1f)
                if (mask <= 0f) continue

                // Grime: a little everywhere, patches worked into the edges
                val e = min(min(fx, w - fx), min(fy, h - fy))
                val g = fbm(fx / w * 5f + off, fy / h * 6.5f)
                var a = (0.025f + 0.09f * g * g + 0.16f * exp(-e / 5f) * smoothstep(0.42f, 0.72f, g)).coerceAtMost(0.3f)
                var r = 88f; var gg = 66f; var b = 36f

                // Uneven glaze: broad faint ripples
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

                // Fingerprint haze, in patches
                val haze = smoothstep(0.6f, 0.78f, fbm(fx / w * 3f + 11f + off, fy / h * 3f + variant)) * 0.06f
                if (haze > 0f) {
                    val na = haze + a * (1f - haze)
                    r = (255f * haze + r * a * (1f - haze)) / na
                    gg = (255f * haze + gg * a * (1f - haze)) / na
                    b = (255f * haze + b * a * (1f - haze)) / na
                    a = na
                }

                // Scratches: thin bright lines
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

                // Dings: dark chips
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

                // Lamp reflection across the upper face, streaked by glaze ripples; scratches flare
                val u = fx / w
                val v = fy / h
                val zoneCentre = 0.2f + 0.08f * (u - 0.5f) * (u - 0.5f) * 4f
                val zone = exp(-((v - zoneCentre) * (v - zoneCentre)) / (2f * 0.1f * 0.1f))
                val streak = smoothstep(0.48f, 0.78f, fbm(u * 2.6f + off * 0.7f, v * 19f + variant * 2.3f))
                val rippleLight = 0.6f + 0.8f * smoothstep(0.35f, 0.75f, ripple)
                var spec = zone * (0.12f + 0.5f * streak) * rippleLight + zone * s * 1.8f
                // Faint sheen lower down, so streaks never end abruptly
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
        evenOut(px)
        return Pair(
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap(),
            Bitmap.createBitmap(sx, w, h, Bitmap.Config.ARGB_8888).asImageBitmap(),
        )
    }

    /**
     * Scales darkening wear (grime, dings, thin glaze) to [GRIME], leaving scratches and haze, so
     * every variant looks equally used.
     */
    private fun evenOut(px: IntArray) {
        var dark = 0.0
        var bright = 0.0
        for (p in px) {
            val change = (p ushr 24) / 255.0 * (luminance(p) - FACE)
            if (change < 0.0) dark += change else bright += change
        }
        if (dark >= 0.0) return
        val k = ((GRIME * px.size - bright) / dark).coerceIn(0.5, 2.0)
        for (i in px.indices) {
            val p = px[i]
            if (luminance(p) >= FACE) continue
            val alpha = ((p ushr 24) * k).toInt().coerceIn(0, 255)
            px[i] = (alpha shl 24) or (p and 0xFFFFFF)
        }
    }

    private fun luminance(p: Int): Double =
        (0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)) / 255.0

    // Rough face ivory, and mean dulling per variant (the eight's mean as first made)
    private const val FACE = 0.8
    private const val GRIME = -0.026

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
