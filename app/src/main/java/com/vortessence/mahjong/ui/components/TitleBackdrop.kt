package com.vortessence.mahjong.ui.components

import android.graphics.Bitmap
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
 * The title photo with film grain, in one shader: scaled to fill (as ContentScale.Crop). Static, so
 * a still screen costs nothing.
 *
 * Grain is monochrome, ~1.5 px clumps, strongest in the midtones as real film's. The hash shows no
 * pattern on whole pixel coordinates (the previous one banded). The photo is drawn slightly
 * downscaled, so four taps, as one would soften alternate rows and columns.
 */
@Composable
fun TitleBackdrop(@DrawableRes photo: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = remember(photo) {
        // Once per app run, not per return to the title
        val bitmap = decoded.getOrPut(photo) {
            BitmapFactory.decodeResource(context.resources, photo, BitmapFactory.Options().apply { inScaled = false })
        }
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

// Main thread only
private val decoded = HashMap<Int, Bitmap>()

// Midtone amplitude, fraction of full scale
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
    // Scale to fill, centred
    float scale = max(res.x / photoSize.x, res.y / photoSize.y);
    float2 u = (p - 0.5 * (res - photoSize * scale)) / scale;
    float t = 0.35;
    float3 rgb = 0.25 * float3(photo.eval(u + float2(-t, -t)).rgb + photo.eval(u + float2(t, -t)).rgb
        + photo.eval(u + float2(-t, t)).rgb + photo.eval(u + float2(t, t)).rgb);

    // Grain: neighbourhood-averaged white noise, shaped by luminance
    float2 cell = floor(p);
    float n = hash21(cell) * 0.4
            + (hash21(cell + float2(1.0, 0.0)) + hash21(cell + float2(0.0, 1.0))
             + hash21(cell - float2(1.0, 0.0)) + hash21(cell - float2(0.0, 1.0))) * 0.15;
    float l = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    rgb += (n - 0.5) * 2.0 * strength * (0.35 + 0.65 * 4.0 * l * (1.0 - l));
    return half4(half3(clamp(rgb, 0.0, 1.0)), 1.0);
}
"""
