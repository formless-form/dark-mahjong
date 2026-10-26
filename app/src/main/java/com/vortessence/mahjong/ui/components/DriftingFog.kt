package com.vortessence.mahjong.ui.components

import android.graphics.RuntimeShader
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Large, soft masses of mist moving slowly and straight from right to left across the title
 * photograph, in three layers composited back to front: each a procession of large masses (one to
 * three screen-widths) with gaps between them; the furthest broadest, palest and slowest,
 * nearer ones lower in the valley and a little quicker (parallax). Masses keep their shape as
 * they travel (no swirling) and are soft and even, denser in their cores, with no bright wispy
 * edges; each layer's top and bottom are ragged and travel with it.
 *
 * Fog has no fine detail, so it is rendered at a third of the screen resolution into its own
 * layer and smoothly scaled up, at about 30 fps. [startSeconds] fixes the moment shown, so
 * previews and tests are repeatable.
 */
@Composable
fun DriftingFog(modifier: Modifier = Modifier, startSeconds: Float = 0f, animate: Boolean = true) {
    if (LocalInspectionMode.current) return
    val shader = remember { RuntimeShader(FOG_AGSL) }
    var time by remember(startSeconds) { mutableFloatStateOf(startSeconds) }
    LaunchedEffect(animate, startSeconds) {
        if (!animate) return@LaunchedEffect
        val origin = System.nanoTime()
        while (isActive) {
            time = startSeconds + (System.nanoTime() - origin) / 1e9f
            delay(33)
        }
    }
    Box(
        modifier
            .layout { measurable, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val small = measurable.measure(
                    Constraints.fixed((w / FOG_SCALE).roundToInt().coerceAtLeast(1), (h / FOG_SCALE).roundToInt().coerceAtLeast(1)),
                )
                layout(w, h) {
                    small.placeWithLayer(0, 0) {
                        scaleX = FOG_SCALE
                        scaleY = FOG_SCALE
                        transformOrigin = TransformOrigin(0f, 0f)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                }
            }
            .drawWithCache {
                shader.setFloatUniform("res", size.width, size.height)
                val brush = ShaderBrush(shader)
                onDrawBehind {
                    shader.setFloatUniform("time", time)
                    drawRect(brush)
                }
            },
    )
}

private const val FOG_SCALE = 3f

private const val FOG_AGSL = """
uniform float2 res;
uniform float time;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), u.x),
               mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), u.x), u.y);
}

// Only broad octaves: large, soft masses with no fine wisps
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * vnoise(p);
        p = p * 2.07 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v / 0.875;
}

// One layer of cloud masses. Each mass keeps its shape (a fixed, gentle warp gives it an
// irregular outline) and the whole layer travels straight from right to left
float cloud(float2 uv, float t, float speed, float scale, float seed) {
    float2 q = float2(uv.x * scale + t * speed, uv.y * scale * 1.3 + seed);
    float2 w = float2(fbm(q * 0.5 + seed) - 0.5, fbm(q * 0.5 + seed + 7.3) - 0.5) * 0.4;
    return fbm(q + w);
}

// A band's weight at height y: soft, with ragged edges that travel with the clouds
float band(float y, float lo, float hi, float2 uv, float t, float speed, float seed) {
    float rag = (vnoise(float2(uv.x * 1.2 + t * speed + seed, 3.3)) - 0.5) * 0.16;
    return smoothstep(lo - 0.16 + rag, lo + 0.14 + rag, y) * (1.0 - smoothstep(hi - 0.14 - rag, hi + 0.16 - rag, y));
}

// Composite one layer over what is behind it (premultiplied)
float4 over(float4 back, float3 col, float a) {
    return float4(col * a + back.rgb * (1.0 - a), a + back.a * (1.0 - a));
}

// Soft, even mist: a little denser in each mass's core, no bright edges
float mass(float d, float lo, float hi) {
    return smoothstep(lo, hi, d) * (0.75 + 0.25 * smoothstep(hi, hi + 0.15, d));
}

half4 main(float2 p) {
    float2 uv = p / res.y;
    float t = time;
    float y = p.y / res.y;
    float4 acc = float4(0.0);

    // Three layers back to front, each a procession of large, soft masses with gaps between
    // them, travelling straight leftwards. Screen speeds (in screen heights per second) rise
    // towards the front for parallax. Noise speed = screen speed x scale keeps them in step
    // Back: the broadest, palest banks
    float s1 = 0.0055;
    acc = over(acc, float3(0.68, 0.72, 0.71),
        mass(cloud(uv, t, s1 * 0.62, 0.62, 0.0), 0.475, 0.7) * band(y, 0.14, 0.6, uv, t, s1 * 1.2, 0.0) * 0.45);
    // Middle: broad masses through the valley
    float s2 = 0.008;
    acc = over(acc, float3(0.60, 0.65, 0.64),
        mass(cloud(uv + 3.1, t, s2 * 0.91, 0.91, 11.0), 0.495, 0.72) * band(y, 0.26, 0.74, uv, t, s2 * 1.2, 4.0) * 0.5);
    // Front: nearer masses, lowest, over the forest
    float s3 = 0.0102;
    acc = over(acc, float3(0.53, 0.58, 0.57),
        mass(cloud(uv + 7.4, t, s3 * 1.27, 1.27, 23.0), 0.52, 0.74) * band(y, 0.42, 0.9, uv, t, s3 * 1.2, 9.0) * 0.475);

    return half4(acc);
}
"""
