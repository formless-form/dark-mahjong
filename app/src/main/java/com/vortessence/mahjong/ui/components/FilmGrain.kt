package com.vortessence.mahjong.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Subtle photographic film grain, as a render effect for a layer showing a photo. Monochrome, soft-edged and
 * strongest in the midtones (fading out in deep shadow and bright highlights, as real film grain
 * does). Static, like a print, so a still screen costs nothing to keep showing.
 *
 * [strength] is the grain amplitude in the midtones as a fraction of full scale (0.035 ≈ 3.5%).
 */
@Composable
fun rememberFilmGrain(strength: Float = 0.035f): androidx.compose.ui.graphics.RenderEffect? {
    if (LocalInspectionMode.current) return null
    return remember(strength) {
        RuntimeShader(FILM_GRAIN_AGSL).let { shader ->
            shader.setFloatUniform("strength", strength)
            RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
    }
}

// Grain is built from hashed white noise averaged over a small neighbourhood, which gives soft
// clumps about 1.5 px across instead of harsh single-pixel static, then shaped by luminance.
private const val FILM_GRAIN_AGSL = """
uniform shader content;
uniform float strength;

float hash21(float2 p) {
    p = fract(p * float2(233.34, 851.73));
    p += dot(p, p + 23.45);
    return fract(p.x * p.y);
}

half4 main(float2 p) {
    half4 c = content.eval(p);
    float2 cell = floor(p);
    float n = hash21(cell) * 0.4
            + (hash21(cell + float2(1.0, 0.0)) + hash21(cell + float2(0.0, 1.0))
             + hash21(cell - float2(1.0, 0.0)) + hash21(cell - float2(0.0, 1.0))) * 0.15;
    n = (n - 0.5) * 2.0;
    float luma = dot(float3(c.rgb), float3(0.2126, 0.7152, 0.0722));
    float midtones = 4.0 * luma * (1.0 - luma);
    float g = n * strength * (0.35 + 0.65 * midtones);
    return half4(clamp(c.rgb + half3(g), 0.0, 1.0), c.a);
}
"""
