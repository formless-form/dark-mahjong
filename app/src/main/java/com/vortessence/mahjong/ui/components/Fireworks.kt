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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A fireworks light show seen from directly above. There are no rockets (their climb is straight
 * towards the viewer, so invisible); shells simply burst. A spherical shell seen from above is a
 * disc of sparks, densest at its rim, that expands, slows, and shrinks and dims as the sparks fall
 * away from the viewer. Bursts overlap densely in widely varied colours, drawn additively so light
 * builds up like real glow. Runs once, then stops drawing.
 */
@Composable
fun FireworksShow(modifier: Modifier = Modifier, seed: Long = System.nanoTime(), onFinished: () -> Unit = {}) {
    val show = remember(seed) { FireworksSimulation(Random(seed)) }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(show) {
        var last = -1L
        while (!show.finished) {
            withFrameNanos { now ->
                // Clamp the step so a dropped frame never makes sparks jump
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

/** Advances a fresh show [seconds] into its run without animating; used for previews and tests. */
internal fun fireworksSnapshot(seed: Long, seconds: Float): (DrawScope) -> Unit {
    val show = FireworksSimulation(Random(seed))
    var t = 0f
    while (t < seconds) {
        show.step(1f / 60f)
        t += 1f / 60f
    }
    return { scope -> show.draw(scope) }
}

/**
 * Single shells burst over the table when a pair is matched, one per match. Each sim lives until
 * its sparks die; several can overlap when the player matches quickly.
 */
internal class MatchBursts {
    private val live = mutableListOf<FireworksSimulation>()

    /** Bumped every frame while a burst is alive, so a Canvas that draws these redraws. */
    var frame by mutableLongStateOf(0L)
        private set

    /**
     * Bursts at ([x], [y]) on a canvas [canvasHeight] tall, with sparks reaching about [reach]
     * pixels out. Suspends until every burst it is running has died away.
     */
    suspend fun burst(x: Float, y: Float, reach: Float, canvasHeight: Float, seed: Long = System.nanoTime()) {
        if (canvasHeight <= 0f) return
        // A spark's speed times its drag-limited travel: 0.40 / 2.1 of the shell size
        val single = SingleBurst(x / canvasHeight, y / canvasHeight, size = reach / canvasHeight / 0.19f)
        live += FireworksSimulation(Random(seed), single)
        if (live.size > 1) return // the first caller's loop already runs every frame
        var last = -1L
        while (live.isNotEmpty()) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0f else ((now - last) / 1e9f).coerceAtMost(1f / 20f)
                last = now
                live.forEach { it.step(dt) }
                live.removeAll { it.finished }
                frame = now
            }
        }
    }

    fun draw(scope: DrawScope) {
        live.forEach { it.draw(scope) }
    }
}

/** A lone shell: where it bursts (in canvas heights) and how big it is. */
private class SingleBurst(val x: Float, val y: Float, val size: Float)

private enum class BurstStyle { PEONY, RING, CHRYSANTHEMUM, WILLOW, CRACKLE, MULTI }

/**
 * Particle state in flat arrays (no allocation per frame). Positions are in units of the canvas
 * height, so bursts look the same on any screen; x runs 0..aspect.
 */
private class FireworksSimulation(private val rnd: Random, private val single: SingleBurst? = null) {
    private val cap = 4200
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
    private val r2 = FloatArray(cap) // colour the spark shifts towards as it ages
    private val g2 = FloatArray(cap)
    private val b2 = FloatArray(cap)
    private val crackle = BooleanArray(cap)
    private val alive = BooleanArray(cap)
    private var freeHint = 0

    private val flashCount = 40
    private val flashX = FloatArray(flashCount)
    private val flashY = FloatArray(flashCount)
    private val flashLife = FloatArray(flashCount)
    private val flashR = FloatArray(flashCount)
    private val flashG = FloatArray(flashCount)
    private val flashB = FloatArray(flashCount)
    private val flashSize = FloatArray(flashCount)

    private var aspect = 0.45f
    private var time = 0f

    // Dense schedule: an opening volley, a busy middle, and a closing barrage
    private val burstTimes: FloatArray = if (single != null) floatArrayOf(0f) else buildList {
        for (i in 0 until 4) add(0.02f + i * 0.09f + rnd.nextFloat() * 0.05f)
        var t = 0.45f
        while (t < 3.3f) {
            add(t)
            t += 0.09f + rnd.nextFloat() * 0.13f
        }
        for (i in 0 until 7) add(3.35f + i * 0.07f + rnd.nextFloat() * 0.05f)
    }.sorted().toFloatArray()
    private var nextBurst = 0
    var finished = false
        private set

    fun step(dt: Float) {
        time += dt
        while (nextBurst < burstTimes.size && time >= burstTimes[nextBurst]) {
            burst()
            nextBurst++
        }
        var anyAlive = false
        for (i in 0 until cap) {
            if (!alive[i]) continue
            life[i] -= dt
            if (life[i] <= 0f) {
                alive[i] = false
                if (i < freeHint) freeHint = i
                continue
            }
            anyAlive = true
            val d = exp(-drag[i] * dt)
            vx[i] *= d
            vy[i] *= d
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
        }
        for (f in 0 until flashCount) if (flashLife[f] > 0f) flashLife[f] -= dt
        finished = nextBurst >= burstTimes.size && !anyAlive
    }

    private fun burst() {
        val cx = single?.x ?: (aspect * (0.1f + 0.8f * rnd.nextFloat()))
        val cy = single?.y ?: (0.08f + 0.84f * rnd.nextFloat())
        // A match's shell is quick and light, so play goes on uncluttered: no long willows
        val styles = if (single != null) BurstStyle.entries - BurstStyle.WILLOW else BurstStyle.entries
        val style = styles[rnd.nextInt(styles.size)]
        // Widely varied colours: any hue, mostly saturated, sometimes pastel or gold/silver
        val (cr, cg, cb) = randomColour()
        val (sr, sg, sb) = randomColour()
        val size = single?.size ?: (0.75f + rnd.nextFloat() * 0.6f)
        val lifeScale = if (single != null) 0.55f else 1f

        val f = (0 until flashCount).firstOrNull { flashLife[it] <= 0f } ?: rnd.nextInt(flashCount)
        flashX[f] = cx; flashY[f] = cy; flashLife[f] = 0.4f
        flashR[f] = cr; flashG[f] = cg; flashB[f] = cb; flashSize[f] = size

        val count = when (style) {
            BurstStyle.RING -> 90
            BurstStyle.WILLOW -> 120
            BurstStyle.CHRYSANTHEMUM -> 170
            BurstStyle.CRACKLE -> 110
            else -> 150
        } / (if (single != null) 2 else 1)
        for (k in 0 until count) {
            // Uniform direction on a sphere, seen from above: (x, y) of the unit vector.
            // Projected, this fills a disc that is densest towards its rim.
            val zDir = rnd.nextFloat() * 2f - 1f
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val rr = sqrt(1f - zDir * zDir)
            var dx = rr * cos(a)
            var dy = rr * sin(a)
            if (style == BurstStyle.RING) {
                // A ring shell is a flat circle; from above it reads as a crisp round ring
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
    ) {
        var i = freeHint
        while (i < cap && alive[i]) i++
        if (i >= cap) return
        freeHint = i + 1
        alive[i] = true
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = l; maxLife[i] = l; this.drag[i] = drag; radius[i] = rad
        twinkle[i] = rnd.nextFloat() * 6.28f
        hot[i] = 0.12f + rnd.nextFloat() * 0.1f
        r[i] = cr; g[i] = cg; b[i] = cb; r2[i] = er; g2[i] = eg; b2[i] = eb
        crackle[i] = crackles
    }

    fun draw(scope: DrawScope) = with(scope) {
        aspect = size.width / size.height
        val h = size.height
        val unit = h / 420f

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
            // Fade in over the first few hundredths of a second, so a fresh burst is a quick soft
            // flash rather than a hard white dot of stacked sparks
            var alpha = t * sqrt(t) * (age / 0.07f).coerceAtMost(1f)
            if (crackle[i] && t < 0.6f) {
                // Crackle: sparks strobe on and off as they die
                alpha *= if (sin(time * 55f + twinkle[i] * 9f) > 0.2f) 1f else 0.1f
            } else if (t < 0.4f) {
                alpha *= 0.55f + 0.45f * sin(time * 24f + twinkle[i])
            }
            if (alpha <= 0.01f) continue
            // White-hot at birth, then the shell's colour, drifting towards its second colour
            val heat = (1f - age / hot[i]).coerceIn(0f, 1f)
            val mix = 1f - t
            val hotWhite = heat * 0.7f
            val cr = (r[i] + (r2[i] - r[i]) * mix) * (1f - hotWhite) + hotWhite
            val cg = (g[i] + (g2[i] - g[i]) * mix) * (1f - hotWhite) + hotWhite
            val cb = (b[i] + (b2[i] - b[i]) * mix) * (1f - hotWhite) + hotWhite
            val c = Color(cr.coerceIn(0f, 1f), cg.coerceIn(0f, 1f), cb.coerceIn(0f, 1f))
            val px = x[i] * h
            val py = y[i] * h
            // Sparks shrink as they fall away from the viewer
            val rr = radius[i] * unit * (0.45f + 0.55f * t)
            drawLine(
                color = c.copy(alpha = alpha * 0.5f),
                start = Offset(px - vx[i] * h * 0.05f, py - vy[i] * h * 0.05f),
                end = Offset(px, py),
                strokeWidth = rr * 1.1f, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
            )
            drawCircle(c.copy(alpha = alpha * 0.16f), radius = rr * 3.6f, center = Offset(px, py), blendMode = BlendMode.Plus)
            drawCircle(c.copy(alpha = alpha), radius = rr, center = Offset(px, py), blendMode = BlendMode.Plus)
        }
    }
}
