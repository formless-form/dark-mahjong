package com.vortessence.mahjong.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import androidx.compose.ui.graphics.nativeCanvas
import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TileSuit

/**
 * Fireworks seen from directly above: no rockets (they climb straight at the viewer); a shell is a
 * disc of sparks, densest at the rim, shrinking and dimming as they fall away, each trailing a
 * streak while fast; some shed glitter, and each leaves a warm haze. Drawn additively so light
 * builds like real glow. Runs once, then stops drawing.
 */
@Composable
fun FireworksShow(
    modifier: Modifier = Modifier,
    seed: Long = System.nanoTime(),
    // Low-performance mode: fewer, smaller shells, no crackle
    lite: Boolean = false,
    onFinished: () -> Unit = {},
) {
    val show = remember(seed, lite) { FireworksSimulation(Random(seed), cap = if (lite) LITE_SHOW_SPARKS else 4200, allowCrackle = !lite, lite = lite, rich = !lite) }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(show) {
        var last = -1L
        while (!show.finished) {
            withFrameNanos { now ->
                // Clamped so a dropped frame never makes sparks jump
                val dt = if (last < 0) 0f else ((now - last) / 1e9f).coerceAtMost(1f / 20f)
                last = now
                show.step(dt)
                frame = now
            }
        }
        onFinished()
    }

    Canvas(modifier) {
        if (frame == 0L) return@Canvas
        show.draw(this)
    }
}

// The full show's is 4200
private const val LITE_SHOW_SPARKS = 900

/** A fresh show advanced [seconds] without animating, for previews and tests. */
internal fun fireworksSnapshot(seed: Long, seconds: Float, lite: Boolean = false): (DrawScope) -> Unit {
    val show = FireworksSimulation(Random(seed), cap = if (lite) LITE_SHOW_SPARKS else 4200, allowCrackle = !lite, lite = lite, rich = !lite)
    var t = 0f
    while (t < seconds) {
        show.step(1f / 60f)
        t += 1f / 60f
    }
    return { scope -> show.draw(scope) }
}

/**
 * One small, cheap shell per match. A reused pool, so a match allocates nothing; quick matches
 * overlap, the oldest giving way when all are busy.
 */
internal class MatchBursts(lite: Boolean = false) {
    private val pool = Array(POOL) {
        FireworksSimulation(Random(it + 1L), SingleBurst(), cap = if (lite) LITE_SPARKS else MATCH_SPARKS, allowCrackle = !lite, rich = !lite)
    }
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Bumped each frame while a burst is alive, so the Canvas redraws. */
    var frame by mutableLongStateOf(0L)
        private set

    val idle: Boolean get() = pool.all { it.finished }

    /** Sparks reach about [reach] px, in [tile]'s face colours if given; [run] animates it. */
    fun burst(x: Float, y: Float, reach: Float, canvasHeight: Float, tile: Tile? = null, seed: Long = System.nanoTime()) {
        if (canvasHeight <= 0f) return
        val sim = pool.firstOrNull { it.finished } ?: pool.minBy { it.startedAt }
        // Spark speed x drag-limited travel: 0.40 / 2.1 of the shell size
        sim.restart(Random(seed), x / canvasHeight, y / canvasHeight, size = reach / canvasHeight / 0.19f, startedAt = seed, palette = tile?.let(::sparkPalette))
        wake.trySend(Unit)
    }

    /** For as long as the board is shown. */
    suspend fun run() {
        while (true) {
            wake.receive()
            var last = -1L
            while (!idle) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0f else ((now - last) / 1e9f).coerceAtMost(1f / 20f)
                    last = now
                    for (sim in pool) if (!sim.finished) sim.step(dt)
                    frame = now
                }
            }
            frame = System.nanoTime()
        }
    }

    /** [canvasHeight] as placed, which may be less than the scope's (the table runs under the toolbar). */
    fun draw(scope: DrawScope, canvasHeight: Float) {
        for (sim in pool) if (!sim.finished) sim.draw(scope, canvasHeight)
    }

    private companion object {
        const val POOL = 3
        // Just enough to read as a shell at tile size
        const val MATCH_SPARKS = 40
        // Low-performance mode: still a sparkle, a third of the drawing
        const val LITE_SPARKS = 14
    }
}

// The tiles' face inks, brightened to burn as sparks
private val INK_RED = Triple(1f, 0.36f, 0.30f)
private val INK_GREEN = Triple(0.32f, 0.95f, 0.42f)
private val INK_BLUE = Triple(0.40f, 0.52f, 1f)
private val SPARK_GOLD = Triple(1f, 0.82f, 0.45f)
private val SPARK_SILVER = Triple(0.92f, 0.95f, 1f)

/** A matched tile's face colours, for its burst. */
private fun sparkPalette(tile: Tile): List<Triple<Float, Float, Float>> = when (tile.suit) {
    TileSuit.CHARACTER -> listOf(INK_RED, INK_BLUE)
    TileSuit.BAMBOO -> listOf(INK_GREEN, INK_GREEN, SPARK_GOLD)
    TileSuit.CIRCLE -> listOf(INK_BLUE, INK_GREEN, INK_RED)
    TileSuit.WIND -> listOf(INK_BLUE, SPARK_SILVER)
    TileSuit.DRAGON -> when (tile.value) {
        1 -> listOf(INK_RED, SPARK_GOLD)
        2 -> listOf(INK_GREEN, SPARK_GOLD)
        else -> listOf(SPARK_SILVER, INK_BLUE)
    }
    TileSuit.SEASON, TileSuit.FLOWER -> listOf(SPARK_GOLD, INK_RED, INK_GREEN, INK_BLUE)
}

/** In canvas heights. */
private class SingleBurst(var x: Float = 0f, var y: Float = 0f, var size: Float = 1f)

private enum class BurstStyle { PEONY, RING, CHRYSANTHEMUM, WILLOW, CRACKLE, MULTI }

/**
 * Flat arrays (no per-frame allocation), up to [cap] sparks. Positions in canvas heights, so any
 * screen looks the same; x runs 0..aspect. A lone shell ([single]) can be [restart]ed.
 */
private class FireworksSimulation(
    private var rnd: Random,
    private val single: SingleBurst? = null,
    private val cap: Int = 4200,
    private val allowCrackle: Boolean = true,
    private val lite: Boolean = false,
    // Trails; for the show also glitter and a lingering haze
    private val rich: Boolean = false,
) {
    private val x = FloatArray(cap)
    private val y = FloatArray(cap)
    private val vx = FloatArray(cap)
    private val vy = FloatArray(cap)
    private val life = FloatArray(cap)
    private val maxLife = FloatArray(cap)
    private val drag = FloatArray(cap)
    private val radius = FloatArray(cap)
    private val twinkle = FloatArray(cap)
    private val hot = FloatArray(cap) // seconds of white-hot glow at birth
    private val r = FloatArray(cap)
    private val g = FloatArray(cap)
    private val b = FloatArray(cap)
    private val r2 = FloatArray(cap) // colour it ages towards
    private val g2 = FloatArray(cap)
    private val b2 = FloatArray(cap)
    private val crackle = BooleanArray(cap)
    private val sheds = BooleanArray(cap) // drops glitter as it flies
    private val fleck = BooleanArray(cap) // a glitter fleck
    private val alive = BooleanArray(cap)
    private var freeHint = 0
    private var liveCount = 0
    private var palette: List<Triple<Float, Float, Float>>? = null

    private val flashCount = if (single != null) 1 else 40
    private val flashX = FloatArray(flashCount)
    private val flashY = FloatArray(flashCount)
    private val flashLife = FloatArray(flashCount)
    private val flashR = FloatArray(flashCount)
    private val flashG = FloatArray(flashCount)
    private val flashB = FloatArray(flashCount)
    private val flashSize = FloatArray(flashCount)

    // Warm smoke lit by each shell, lingering after it
    private val hazeCount = if (rich && single == null) 40 else 0
    private val hazeX = FloatArray(hazeCount)
    private val hazeY = FloatArray(hazeCount)
    private val hazeLife = FloatArray(hazeCount)
    private val hazeR = FloatArray(hazeCount)
    private val hazeG = FloatArray(hazeCount)
    private val hazeB = FloatArray(hazeCount)
    private val hazeSize = FloatArray(hazeCount)

    private var aspect = 0.45f
    private var time = 0f

    // Opening volley, busy middle, closing barrage; lite has about a third of the shells
    private val burstTimes: FloatArray = if (single != null) floatArrayOf(0f) else if (lite) buildList {
        for (i in 0 until 2) add(0.02f + i * 0.15f + rnd.nextFloat() * 0.05f)
        var t = 0.6f
        while (t < 3.3f) {
            add(t)
            t += 0.3f + rnd.nextFloat() * 0.3f
        }
        for (i in 0 until 3) add(3.35f + i * 0.12f + rnd.nextFloat() * 0.05f)
    }.sorted().toFloatArray() else buildList {
        for (i in 0 until 4) add(0.02f + i * 0.09f + rnd.nextFloat() * 0.05f)
        var t = 0.45f
        while (t < 3.3f) {
            add(t)
            t += 0.09f + rnd.nextFloat() * 0.13f
        }
        for (i in 0 until 7) add(3.35f + i * 0.07f + rnd.nextFloat() * 0.05f)
    }.sorted().toFloatArray()
    private var nextBurst = 0
    var finished = single != null
        private set
    var startedAt = 0L
        private set

    /** In canvas heights. */
    fun restart(random: Random, x: Float, y: Float, size: Float, startedAt: Long, palette: List<Triple<Float, Float, Float>>? = null) {
        val s = single ?: return
        rnd = random
        this.palette = palette
        s.x = x; s.y = y; s.size = size
        this.startedAt = startedAt
        java.util.Arrays.fill(alive, false)
        java.util.Arrays.fill(flashLife, 0f)
        freeHint = 0
        time = 0f
        nextBurst = 0
        finished = false
    }

    fun step(dt: Float) {
        time += dt
        while (nextBurst < burstTimes.size && time >= burstTimes[nextBurst]) {
            burst()
            nextBurst++
        }
        var live = 0
        for (i in 0 until cap) {
            if (!alive[i]) continue
            life[i] -= dt
            if (life[i] <= 0f) {
                alive[i] = false
                if (i < freeHint) freeHint = i
                continue
            }
            live++
            val d = exp(-drag[i] * dt)
            vx[i] *= d
            vy[i] *= d
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            // Glitter, kept to three quarters of the sparks so new shells always find room
            if (sheds[i] && liveCount < cap * 3 / 4 && rnd.nextFloat() < GLITTER_RATE * dt) shed(i)
        }
        liveCount = live
        for (f in 0 until flashCount) if (flashLife[f] > 0f) flashLife[f] -= dt
        var anyHaze = false
        for (k in 0 until hazeCount) if (hazeLife[k] > 0f) {
            hazeLife[k] -= dt
            anyHaze = true
        }
        finished = nextBurst >= burstTimes.size && live == 0 && !anyHaze
    }

    private fun burst() {
        val cx = single?.x ?: (aspect * (0.1f + 0.8f * rnd.nextFloat()))
        val cy = single?.y ?: (0.08f + 0.84f * rnd.nextFloat())
        // No long willows for a match, to keep play uncluttered
        val styles = if (single != null) BurstStyle.entries - BurstStyle.WILLOW else BurstStyle.entries
        val style = styles[rnd.nextInt(styles.size)]
        // Any hue, mostly saturated, sometimes pastel or gold/silver
        val (cr, cg, cb) = palette?.let { it[rnd.nextInt(it.size)] } ?: randomColour()
        val (sr, sg, sb) = palette?.let { it[rnd.nextInt(it.size)] } ?: randomColour()
        val size = single?.size ?: (0.75f + rnd.nextFloat() * 0.6f)
        val lifeScale = if (single != null) 0.55f else 1f

        val f = (0 until flashCount).firstOrNull { flashLife[it] <= 0f } ?: rnd.nextInt(flashCount)
        flashX[f] = cx; flashY[f] = cy; flashLife[f] = 0.4f
        flashR[f] = cr; flashG[f] = cg; flashB[f] = cb; flashSize[f] = size
        if (hazeCount > 0) {
            val k = (0 until hazeCount).firstOrNull { hazeLife[it] <= 0f } ?: rnd.nextInt(hazeCount)
            hazeX[k] = cx; hazeY[k] = cy; hazeLife[k] = HAZE_LIFE; hazeSize[k] = size
            // Half the shell's colour, half the warm smoke it lights
            hazeR[k] = 0.5f * cr + 0.5f; hazeG[k] = 0.5f * cg + 0.3f; hazeB[k] = 0.5f * cb + 0.15f
        }
        // About one show shell in three trails glitter
        val glitters = hazeCount > 0 && style != BurstStyle.WILLOW && rnd.nextFloat() < 0.3f

        val count = when (style) {
            BurstStyle.RING -> 90
            BurstStyle.WILLOW -> 120
            BurstStyle.CHRYSANTHEMUM -> 170
            BurstStyle.CRACKLE -> 110
            else -> 150
        }.let {
            when {
                single != null -> (it * cap / 150).coerceAtMost(cap)
                lite -> it * 2 / 5
                else -> it
            }
        }
        for (k in 0 until count) {
            // Uniform on a sphere, projected from above: a disc densest at its rim
            val zDir = rnd.nextFloat() * 2f - 1f
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val rr = sqrt(1f - zDir * zDir)
            var dx = rr * cos(a)
            var dy = rr * sin(a)
            if (style == BurstStyle.RING) {
                // Flat circle: a crisp ring from above
                val ra = k.toFloat() / count * 2f * PI.toFloat()
                dx = cos(ra); dy = sin(ra)
            }
            val speed = size * when (style) {
                BurstStyle.WILLOW -> 0.30f
                BurstStyle.RING -> 0.42f
                BurstStyle.CHRYSANTHEMUM -> 0.46f
                else -> 0.40f
            } * (0.92f + rnd.nextFloat() * 0.16f)
            val second = style == BurstStyle.MULTI && rnd.nextFloat() < 0.5f
            val pr = if (second) sr else cr
            val pg = if (second) sg else cg
            val pb = if (second) sb else cb
            val l = when (style) {
                BurstStyle.WILLOW -> 2.4f + rnd.nextFloat() * 0.9f
                BurstStyle.CHRYSANTHEMUM -> 1.5f + rnd.nextFloat() * 0.5f
                else -> 1.1f + rnd.nextFloat() * 0.6f
            } * lifeScale
            // Willows burn gold; chrysanthemums fade towards their second colour
            val (er, eg, eb) = when (style) {
                BurstStyle.WILLOW -> Triple(1f, 0.72f, 0.32f)
                BurstStyle.CHRYSANTHEMUM -> Triple(sr, sg, sb)
                else -> Triple(pr * 0.8f, pg * 0.8f, pb * 0.8f)
            }
            spawn(
                cx, cy, dx * speed, dy * speed, l,
                drag = if (style == BurstStyle.WILLOW) 2.6f else 2.1f,
                rad = (if (style == BurstStyle.WILLOW) 0.85f else 1.05f) * (if (single != null) 0.7f else 1f),
                cr = if (style == BurstStyle.WILLOW) 1f else pr,
                cg = if (style == BurstStyle.WILLOW) 0.8f else pg,
                cb = if (style == BurstStyle.WILLOW) 0.45f else pb,
                er = er, eg = eg, eb = eb,
                crackles = style == BurstStyle.CRACKLE,
                sheds = glitters,
            )
        }
    }

    private fun randomColour(): Triple<Float, Float, Float> {
        val pick = rnd.nextFloat()
        return when {
            pick < 0.10f -> Triple(1f, 0.82f, 0.45f) // gold
            pick < 0.17f -> Triple(0.92f, 0.95f, 1f) // silver
            else -> {
                val h = rnd.nextFloat() * 360f
                val s = if (rnd.nextFloat() < 0.2f) 0.45f else 0.85f + rnd.nextFloat() * 0.15f
                hsv(h, s, 1f)
            }
        }
    }

    private fun hsv(h: Float, s: Float, v: Float): Triple<Float, Float, Float> {
        val c = v * s
        val hp = (h / 60f) % 6f
        val xx = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, xx, 0f)
            hp < 2f -> Triple(xx, c, 0f)
            hp < 3f -> Triple(0f, c, xx)
            hp < 4f -> Triple(0f, xx, c)
            hp < 5f -> Triple(xx, 0f, c)
            else -> Triple(c, 0f, xx)
        }
        val m = v - c
        return Triple(r1 + m, g1 + m, b1 + m)
    }

    private fun spawn(
        px: Float, py: Float, pvx: Float, pvy: Float, l: Float, drag: Float, rad: Float,
        cr: Float, cg: Float, cb: Float, er: Float, eg: Float, eb: Float, crackles: Boolean,
        sheds: Boolean = false, fleck: Boolean = false,
    ): Boolean {
        var i = freeHint
        while (i < cap && alive[i]) i++
        if (i >= cap) return false
        freeHint = i + 1
        alive[i] = true
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = l; maxLife[i] = l; this.drag[i] = drag; radius[i] = rad
        twinkle[i] = rnd.nextFloat() * 6.28f
        hot[i] = 0.12f + rnd.nextFloat() * 0.1f
        r[i] = cr; g[i] = cg; b[i] = cb; r2[i] = er; g2[i] = eg; b2[i] = eb
        crackle[i] = crackles && allowCrackle
        this.sheds[i] = sheds
        this.fleck[i] = fleck
        return true
    }

    // A glitter fleck left behind by spark [i]: barely moving, warm white to gold, twinkling out
    private fun shed(i: Int) {
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val drift = 0.012f * rnd.nextFloat()
        spawn(
            x[i], y[i], vx[i] * 0.08f + drift * cos(a), vy[i] * 0.08f + drift * sin(a), 0.5f + rnd.nextFloat() * 0.6f,
            drag = 3f, rad = 0.5f, cr = 1f, cg = 0.92f, cb = 0.72f, er = 1f, eg = 0.68f, eb = 0.3f,
            crackles = false, fleck = true,
        )
    }

    /** Positions are in units of [height]. */
    fun draw(scope: DrawScope, height: Float = scope.size.height) = with(scope) {
        aspect = size.width / height
        val h = height
        val unit = h / 420f

        val canvas = drawContext.canvas.nativeCanvas
        for (k in 0 until hazeCount) {
            if (hazeLife[k] <= 0f) continue
            val age = HAZE_LIFE - hazeLife[k]
            val left = hazeLife[k] / HAZE_LIFE
            val a = (age / 0.25f).coerceAtMost(1f) * left * sqrt(left)
            val c = Offset(hazeX[k] * h, hazeY[k] * h)
            // Spreading as it drifts
            val rad = h * 0.26f * hazeSize[k] * (0.8f + 0.5f * age / HAZE_LIFE)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(hazeR[k].coerceAtMost(1f), hazeG[k].coerceAtMost(1f), hazeB[k].coerceAtMost(1f), 0.075f * a), Color.Transparent),
                    center = c, radius = rad,
                ),
                radius = rad, center = c, blendMode = BlendMode.Plus,
            )
        }
        for (f in 0 until flashCount) {
            if (flashLife[f] <= 0f) continue
            val a = flashLife[f] / 0.4f
            val c = Offset(flashX[f] * h, flashY[f] * h)
            val rad = h * 0.16f * flashSize[f]
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(flashR[f], flashG[f], flashB[f], 0.30f * a * a), Color.Transparent),
                    center = c, radius = rad,
                ),
                radius = rad, center = c, blendMode = BlendMode.Plus,
            )
        }

        for (i in 0 until cap) {
            if (!alive[i]) continue
            val t = life[i] / maxLife[i] // 1 at birth, 0 at death
            val age = maxLife[i] - life[i]
            // Fade in over ~0.07 s, so a fresh burst is a soft flash, not a hard dot of stacked sparks
            var alpha = t * sqrt(t) * (age / 0.07f).coerceAtMost(1f)
            if (fleck[i]) {
                // Glitter: a hard twinkle all its life
                alpha *= if (sin(time * 38f + twinkle[i] * 7f) > 0f) 1f else 0.15f
            } else if (crackle[i] && t < 0.6f) {
                // Crackle: dying sparks strobe
                alpha *= if (sin(time * 55f + twinkle[i] * 9f) > 0.2f) 1f else 0.1f
            } else if (t < 0.4f) {
                alpha *= 0.55f + 0.45f * sin(time * 24f + twinkle[i])
            }
            if (alpha <= 0.01f) continue
            // White-hot, then the shell's colour, drifting to its second
            val heat = (1f - age / hot[i]).coerceIn(0f, 1f)
            val mix = 1f - t
            val hotWhite = heat * 0.7f
            val cr = (r[i] + (r2[i] - r[i]) * mix) * (1f - hotWhite) + hotWhite
            val cg = (g[i] + (g2[i] - g[i]) * mix) * (1f - hotWhite) + hotWhite
            val cb = (b[i] + (b2[i] - b[i]) * mix) * (1f - hotWhite) + hotWhite
            val px = x[i] * h
            val py = y[i] * h
            // Shrinking as they fall away
            val rr = radius[i] * unit * (0.45f + 0.55f * t)
            // One tinted stamp of the glow image
            sparkPaint.color = android.graphics.Color.argb(
                alpha.coerceIn(0f, 1f), cr.coerceIn(0f, 1f), cg.coerceIn(0f, 1f), cb.coerceIn(0f, 1f),
            )
            if (rich && !fleck[i]) {
                // Trail: the path of the last moment, longest while fast, gone as it slows
                val len = sqrt(vx[i] * vx[i] + vy[i] * vy[i]) * h * (if (single != null) MATCH_TRAIL_S else TRAIL_S)
                if (len > rr) {
                    trailPaint.color = android.graphics.Color.argb(
                        (alpha * 0.8f).coerceIn(0f, 1f), cr.coerceIn(0f, 1f), cg.coerceIn(0f, 1f), cb.coerceIn(0f, 1f),
                    )
                    trailMatrix.setTranslate(-TrailSprite.LENGTH.toFloat(), -TrailSprite.WIDTH / 2f)
                    trailMatrix.postScale(len / TrailSprite.LENGTH, rr * 2.6f / TrailSprite.WIDTH)
                    trailMatrix.postRotate(Math.toDegrees(atan2(vy[i], vx[i]).toDouble()).toFloat())
                    trailMatrix.postTranslate(px, py)
                    canvas.drawBitmap(TrailSprite.bitmap, trailMatrix, trailPaint)
                }
            }
            val reach = rr * SparkSprite.REACH
            sparkRect.set(px - reach, py - reach, px + reach, py + reach)
            canvas.drawBitmap(SparkSprite.bitmap, null, sparkRect, sparkPaint)
        }
    }

    private val trailMatrix = android.graphics.Matrix()
    private val trailPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
    }

    private val sparkRect = android.graphics.RectF()
    private val sparkPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
    }
}

// Seconds of flight a trail shows; a match's is shorter, to keep play uncluttered
private const val TRAIL_S = 0.18f
private const val MATCH_TRAIL_S = 0.13f
// Glitter flecks a shedding spark drops per second
private const val GLITTER_RATE = 9f
private const val HAZE_LIFE = 2.2f

/** A trail's glow, head at the right: fading to nothing at its tail, soft across. Alpha-only. */
private object TrailSprite {
    const val LENGTH = 64
    const val WIDTH = 16

    val bitmap: android.graphics.Bitmap by lazy {
        val pixels = ByteArray(LENGTH * WIDTH)
        for (j in 0 until WIDTH) for (i in 0 until LENGTH) {
            val along = (i + 0.5f) / LENGTH
            val across = abs((j + 0.5f) / WIDTH * 2f - 1f)
            val a = along * sqrt(along) * (1f - across * across).coerceAtLeast(0f)
            pixels[j * LENGTH + i] = (a * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
        android.graphics.Bitmap.createBitmap(LENGTH, WIDTH, android.graphics.Bitmap.Config.ALPHA_8).apply {
            copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
        }
    }
}

/**
 * A spark's glow: a core [REACH] times smaller than the image in a fading halo. Alpha-only, so each
 * stamp takes its paint's colour.
 */
private object SparkSprite {
    const val REACH = 3.6f
    private const val SIZE = 64

    val bitmap: android.graphics.Bitmap by lazy {
        val pixels = ByteArray(SIZE * SIZE)
        val c = SIZE / 2f
        val core = 1f / REACH
        for (j in 0 until SIZE) for (i in 0 until SIZE) {
            val dx = (i + 0.5f - c) / c
            val dy = (j + 0.5f - c) / c
            val d = sqrt(dx * dx + dy * dy)
            // A ~1 px core edge stays crisp when small
            val inCore = ((core - d) * c + 0.5f).coerceIn(0f, 1f)
            val halo = 0.16f * (1f - ((d - 0.7f) / 0.3f).coerceIn(0f, 1f))
            val a = inCore + halo * (1f - inCore)
            pixels[j * SIZE + i] = (a * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
        android.graphics.Bitmap.createBitmap(SIZE, SIZE, android.graphics.Bitmap.Config.ALPHA_8).apply {
            copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
        }
    }
}
