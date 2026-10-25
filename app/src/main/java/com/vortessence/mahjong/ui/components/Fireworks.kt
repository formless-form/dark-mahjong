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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A short, soft fireworks light show for clearing a board: rockets rise, burst into glowing
 * sparks (round shells, rings and slow golden willows) that drift, twinkle and fade.
 * Drawn additively so overlapping light builds up like real glow. Runs once, then stops drawing.
 */
@Composable
fun FireworksShow(modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val show = remember { FireworksSimulation(Random(System.nanoTime())) }
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

private val Palette = arrayOf(
    Color(0xFF9FE0C2), // jade
    Color(0xFFF2C27E), // amber
    Color(0xFFCFE2F2), // moonlight
    Color(0xFFF0A9A2), // rose
    Color(0xFFFFF1D6), // warm white
)
private const val GOLD = 1

/**
 * Particle state in flat arrays (no allocation per frame). Positions are in units of the canvas
 * height, so bursts look the same on any screen; x runs 0..aspect.
 */
private class FireworksSimulation(private val rnd: Random) {
    private val cap = 1800
    private val x = FloatArray(cap)
    private val y = FloatArray(cap)
    private val vx = FloatArray(cap)
    private val vy = FloatArray(cap)
    private val life = FloatArray(cap)
    private val maxLife = FloatArray(cap)
    private val colour = IntArray(cap)
    private val drag = FloatArray(cap)
    private val gravity = FloatArray(cap)
    private val radius = FloatArray(cap)
    private val twinkle = FloatArray(cap)
    // kind: 0 = free, 1 = rocket, 2 = spark, 3 = trail ember
    private val kind = IntArray(cap)
    private val burstStyle = IntArray(cap)
    private val burstColour2 = IntArray(cap)

    private val flashX = FloatArray(16)
    private val flashY = FloatArray(16)
    private val flashLife = FloatArray(16)
    private val flashColour = IntArray(16)

    private var aspect = 2.2f
    private var time = 0f
    private val launchTimes = floatArrayOf(0.05f, 0.45f, 0.8f, 1.3f, 1.65f, 2.2f, 2.6f, 3.2f)
        .map { it + rnd.nextFloat() * 0.15f }.toFloatArray()
    private var nextLaunch = 0
    var finished = false
        private set

    fun step(dt: Float) {
        time += dt
        while (nextLaunch < launchTimes.size && time >= launchTimes[nextLaunch]) {
            launchRocket(nextLaunch)
            nextLaunch++
        }

        var alive = 0
        for (i in 0 until cap) {
            if (kind[i] == 0) continue
            life[i] -= dt
            if (life[i] <= 0f) {
                if (kind[i] == 1) explode(i)
                kind[i] = 0
                continue
            }
            alive++
            val d = exp(-drag[i] * dt)
            vx[i] *= d
            vy[i] = vy[i] * d + gravity[i] * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            if (kind[i] == 1 && rnd.nextFloat() < 0.8f) {
                spawn(3, x[i], y[i], (rnd.nextFloat() - 0.5f) * 0.03f, 0.05f + rnd.nextFloat() * 0.05f,
                    0.35f + rnd.nextFloat() * 0.25f, GOLD, 2.5f, 0.15f, 0.55f)
            }
        }
        for (f in flashLife.indices) if (flashLife[f] > 0f) flashLife[f] -= dt

        finished = nextLaunch >= launchTimes.size && alive == 0
    }

    private fun launchRocket(n: Int) {
        // Spread launches across the width, alternating sides so bursts don't pile up
        val lane = (n * 0.618f) % 1f
        val px = aspect * (0.15f + 0.7f * lane)
        val apexTime = 0.9f + rnd.nextFloat() * 0.35f
        val i = spawn(1, px, 1.05f, (rnd.nextFloat() - 0.5f) * 0.06f, -(1.1f + rnd.nextFloat() * 0.25f),
            apexTime, rnd.nextInt(Palette.size), 0.9f, 0.18f, 1.6f)
        if (i >= 0) {
            burstStyle[i] = when (n % 4) { 1 -> 1; 3 -> 2; else -> 0 }
            burstColour2[i] = rnd.nextInt(Palette.size)
        }
    }

    private fun explode(r: Int) {
        val cx = x[r]
        val cy = y[r]
        val style = burstStyle[r]
        val f = flashLife.indices.firstOrNull { flashLife[it] <= 0f } ?: 0
        flashX[f] = cx; flashY[f] = cy; flashLife[f] = 0.35f; flashColour[f] = colour[r]

        val count = when (style) { 1 -> 70; 2 -> 90; else -> 110 }
        for (k in 0 until count) {
            val a = (k.toFloat() / count) * 2f * PI.toFloat() + rnd.nextFloat() * 0.12f
            when (style) {
                // Ring: one crisp circle of sparks
                1 -> {
                    val s = 0.42f + rnd.nextFloat() * 0.03f
                    spawn(2, cx, cy, cos(a) * s, sin(a) * s, 1.3f + rnd.nextFloat() * 0.4f,
                        colour[r], 1.9f, 0.16f, 1.15f)
                }
                // Willow: slow golden drips that hang and fall
                2 -> {
                    val s = 0.32f * sqrt(rnd.nextFloat())
                    spawn(2, cx, cy, cos(a) * s, sin(a) * s, 2.2f + rnd.nextFloat() * 0.8f,
                        GOLD, 1.3f, 0.12f, 0.95f)
                }
                // Shell: a full sphere of two-tone sparks
                else -> {
                    val s = 0.45f * (0.55f + 0.45f * sqrt(rnd.nextFloat()))
                    val c = if (rnd.nextFloat() < 0.65f) colour[r] else burstColour2[r]
                    spawn(2, cx, cy, cos(a) * s, sin(a) * s, 1.4f + rnd.nextFloat() * 0.7f,
                        c, 2.1f, 0.2f, 1.1f)
                }
            }
        }
    }

    private fun spawn(
        k: Int, px: Float, py: Float, pvx: Float, pvy: Float, l: Float, c: Int,
        dr: Float, g: Float, sz: Float,
    ): Int {
        for (i in 0 until cap) {
            if (kind[i] != 0) continue
            kind[i] = k; x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
            life[i] = l; maxLife[i] = l; colour[i] = c; drag[i] = dr; gravity[i] = g
            radius[i] = sz; twinkle[i] = rnd.nextFloat() * 6.28f
            return i
        }
        return -1
    }

    fun draw(scope: DrawScope) = with(scope) {
        aspect = size.width / size.height
        val h = size.height
        val unit = h / 400f

        for (f in flashLife.indices) {
            if (flashLife[f] <= 0f) continue
            val a = (flashLife[f] / 0.35f)
            val centre = Offset(flashX[f] * h, flashY[f] * h)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Palette[flashColour[f]].copy(alpha = 0.22f * a), Color.Transparent),
                    center = centre, radius = h * 0.22f,
                ),
                radius = h * 0.22f, center = centre, blendMode = BlendMode.Plus,
            )
        }

        for (i in 0 until cap) {
            if (kind[i] == 0) continue
            val t = life[i] / maxLife[i]
            var alpha = if (kind[i] == 1) 1f else t * sqrt(t)
            // Sparks glitter as they die
            if (kind[i] == 2 && t < 0.45f) {
                alpha *= 0.55f + 0.45f * sin(time * 26f + twinkle[i])
            }
            if (alpha <= 0.01f) continue
            val c = Palette[colour[i]]
            val px = x[i] * h
            val py = y[i] * h
            val r = radius[i] * unit
            // Motion streak, soft halo, bright core
            drawLine(
                color = c.copy(alpha = alpha * 0.55f),
                start = Offset(px - vx[i] * h * 0.045f, py - vy[i] * h * 0.045f),
                end = Offset(px, py),
                strokeWidth = r * 1.1f, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
            )
            drawCircle(c.copy(alpha = alpha * 0.18f), radius = r * 3.4f, center = Offset(px, py), blendMode = BlendMode.Plus)
            drawCircle(c.copy(alpha = alpha), radius = r, center = Offset(px, py), blendMode = BlendMode.Plus)
        }
    }
}
