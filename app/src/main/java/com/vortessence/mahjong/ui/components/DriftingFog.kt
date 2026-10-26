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
 * Clouds of mist moving across the title photograph. Three layers at different depths and speeds
 * (broad slow banks behind, mid mist, quicker wisps in front), each billowing as it goes: its
 * shape is folded by noise fields that themselves evolve, so it churns rather than slides. Cloud
 * tops catch the sky's light, undersides are cooler; density becomes opacity the way real mist
 * thins (Beer-Lambert), so every edge is soft, and the band's own top and bottom are ragged.
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

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// One cloud layer's density: drifting sideways while folding in on itself. The warp fields
// evolve with time, so the cloud billows and churns instead of sliding as a fixed pattern
float cloud(float2 uv, float t, float speed, float scale, float seed) {
    float2 q = float2(uv.x * scale + t * speed, uv.y * scale * 1.6 + seed);
    float2 w1 = float2(fbm(q * 0.6 + float2(seed, t * 0.03)),
                       fbm(q * 0.6 + float2(seed + 5.2, -t * 0.025)));
    float2 w2 = float2(fbm(q + 2.0 * w1 + float2(1.7, 9.2) + t * 0.02),
                       fbm(q + 2.0 * w1 + float2(8.3, 2.8) - t * 0.015));
    return fbm(q + 2.2 * w2);
}

half4 main(float2 p) {
    float2 uv = p / res.y;
    float t = time;
    // Three depths: broad slow banks, mid mist, quicker wisps in front (parallax)
    float d1 = cloud(uv, t, 0.012, 1.1, 0.0);
    float d2 = cloud(uv + 3.1, t, 0.022, 1.9, 11.0);
    float d3 = cloud(uv + 7.4, t, 0.038, 3.2, 23.0);
    float od = max(d1 - 0.42, 0.0) * 2.2 + max(d2 - 0.48, 0.0) * 1.5 + max(d3 - 0.56, 0.0) * 1.0;

    // Heaviest in the valley; the band's top and bottom are ragged and shifting, never straight
    float y = p.y / res.y;
    float rag = (vnoise(float2(uv.x * 2.2 + t * 0.02, 3.3)) - 0.5) * 0.16
              + (vnoise(float2(uv.x * 6.0 - t * 0.03, 7.1)) - 0.5) * 0.06;
    float band = smoothstep(0.04 + rag, 0.36 + rag, y) * (1.0 - smoothstep(0.56 - rag, 0.92 - rag, y));
    od *= 0.25 + 0.75 * band;

    // Density to opacity as real mist thins: soft everywhere, never a hard edge
    float alpha = (1.0 - exp(-od * 1.7)) * 0.6;

    // Light from the sky above: tops brighter, undersides cooler and greyer
    float above = cloud(uv - float2(0.0, 0.025), t, 0.012, 1.1, 0.0);
    float lit = clamp(0.62 + (d1 - above) * 5.0, 0.25, 1.0);
    float3 col = mix(float3(0.40, 0.45, 0.45), float3(0.72, 0.76, 0.74), lit);
    return half4(half3(col * alpha), half(alpha));
}
"""
