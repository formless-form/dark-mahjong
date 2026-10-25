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
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Slow mist drifting sideways across the title photograph: two layers of soft, warped noise at
 * different speeds (parallax), gently changing shape as they go, thickest in the valley band.
 * Fog is soft, so it is redrawn at about 30 fps rather than every display frame.
 *
 * [startSeconds] fixes the starting moment, so previews and tests are repeatable.
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
        modifier.drawWithCache {
            shader.setFloatUniform("res", size.width, size.height)
            val brush = ShaderBrush(shader)
            onDrawBehind {
                shader.setFloatUniform("time", time)
                drawRect(brush)
            }
        },
    )
}

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
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.07 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

half4 main(float2 p) {
    float2 uv = p / res.y;
    float t = time;
    // Far layer: broad banks of mist, slowly warping as they drift
    float2 q1 = float2(uv.x * 1.6 + t * 0.018, uv.y * 3.2);
    float2 w = float2(fbm(q1 * 0.7 + float2(0.0, t * 0.010)), fbm(q1 * 0.7 + float2(5.2, 1.3) - t * 0.008));
    float f1 = fbm(q1 + 1.4 * w);
    // Near layer: thinner wisps, moving a little faster
    float2 q2 = float2(uv.x * 2.6 + t * 0.05 + 3.0, uv.y * 4.6 + 1.7);
    float f2 = fbm(q2 + 0.8 * float2(fbm(q2 * 0.5 + t * 0.012), 0.0));
    float fog = smoothstep(0.42, 0.85, f1) * 0.7 + smoothstep(0.52, 0.9, f2) * 0.45;
    // Thickest in the valley band, thin near the top and bottom of the screen
    float y = p.y / res.y;
    float band = smoothstep(0.10, 0.36, y) * (1.0 - smoothstep(0.62, 0.88, y));
    float a = clamp(fog * (0.25 + 0.75 * band) * 0.4, 0.0, 1.0);
    float3 mist = float3(0.60, 0.65, 0.63);
    return half4(half3(mist * a), half(a));
}
"""
