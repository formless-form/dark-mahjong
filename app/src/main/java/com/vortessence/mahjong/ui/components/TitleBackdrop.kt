package com.vortessence.mahjong.ui.components

import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext

/**
 * The title screen's photograph with subtle film grain, drawn by one shader: scaled to fill (as
 * ContentScale.Crop), never stretched. Still, like a print, so a still screen costs nothing to keep
 * showing.
 *
 * The grain is monochrome, soft-edged (clumps about 1.5 px across) and strongest in the midtones,
 * fading out in deep shadow and bright highlights as real film grain does. Its random numbers come
 * from a hash that shows no pattern on whole pixel coordinates; the simpler one used before
 * repeated along rows and columns, which showed as faint banding. The photo is shown a little
 * smaller than it is, so it is sampled with four taps rather than one, which would soften
 * alternate columns and rows.
 */
@Composable
fun TitleBackdrop(@DrawableRes photo: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = remember(photo) {
        val bitmap = BitmapFactory.decodeResource(context.resources, photo, BitmapFactory.Options().apply { inScaled = false })
        Triple(
            BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR },
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
        )
    }
    val shader = remember { RuntimeShader(BACKDROP_AGSL).apply { setFloatUniform("strength", GRAIN) } }
    Canvas(modifier) {
        shader.setFloatUniform("res", size.width, size.height)
        shader.setInputShader("photo", image.first)
        shader.setFloatUniform("photoSize", image.second, image.third)
        drawRect(ShaderBrush(shader))
    }
}

// The grain's amplitude in the midtones, as a fraction of full scale
private const val GRAIN = 0.035f

private const val BACKDROP_AGSL = """
uniform float2 res;
uniform shader photo;
uniform float2 photoSize;
uniform float strength;

// Dave Hoskins' "hash without sine"
float hash21(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 main(float2 p) {
    // Scaled to fill, centred: screen pixel to photo pixel
    float scale = max(res.x / photoSize.x, res.y / photoSize.y);
    float2 u = (p - 0.5 * (res - photoSize * scale)) / scale;
    float t = 0.35;
    float3 rgb = 0.25 * float3(photo.eval(u + float2(-t, -t)).rgb + photo.eval(u + float2(t, -t)).rgb
        + photo.eval(u + float2(-t, t)).rgb + photo.eval(u + float2(t, t)).rgb);

    // Grain: white noise averaged over a small neighbourhood, shaped by luminance
    float2 cell = floor(p);
    float n = hash21(cell) * 0.4
            + (hash21(cell + float2(1.0, 0.0)) + hash21(cell + float2(0.0, 1.0))
             + hash21(cell - float2(1.0, 0.0)) + hash21(cell - float2(0.0, 1.0))) * 0.15;
    float l = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    rgb += (n - 0.5) * 2.0 * strength * (0.35 + 0.65 * 4.0 * l * (1.0 - l));
    return half4(half3(clamp(rgb, 0.0, 1.0)), 1.0);
}
"""
