package com.vortessence.mahjong.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.vortessence.mahjong.R
import com.vortessence.mahjong.data.model.TableSurface

/**
 * Draws the tabletop as one full-screen AGSL shader: procedural walnut or green felt, or a real
 * photograph of a woven sedge mat (the Thai straw mat), seen through [TableCamera]'s tilted perspective and lit by a single warm lamp above the
 * middle of the table, with the room falling away into darkness around it.
 */
object TableSurfaceRenderer {

    private var shader: RuntimeShader? = null
    private var matBitmap: Bitmap? = null

    fun drawTableSurface(
        scope: DrawScope,
        surface: TableSurface,
        width: Float,
        height: Float,
        context: Context? = null,
    ) {
        val s = shader ?: RuntimeShader(TABLE_AGSL).also { shader = it }
        val mat = matBitmap ?: context?.let { ctx ->
            BitmapFactory.decodeResource(ctx.resources, R.drawable.thai_mat).also { matBitmap = it }
        }
        if (mat != null) {
            // Mirrored tiling is seamless for a woven mat; linear filtering keeps strands smooth
            s.setInputShader(
                "matTex",
                BitmapShader(mat, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR).apply {
                    filterMode = BitmapShader.FILTER_MODE_LINEAR
                },
            )
            s.setFloatUniform("matSize", mat.width.toFloat(), mat.height.toFloat())
        } else {
            s.setInputShader("matTex", BitmapShader(PLACEHOLDER, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT))
            s.setFloatUniform("matSize", 1f, 1f)
        }
        s.setFloatUniform("res", width, height)
        s.setFloatUniform("mode", surface.ordinal.toFloat())
        s.setFloatUniform("tilt", Math.toRadians(TableCamera.TILT_DEGREES.toDouble()).toFloat())
        s.setFloatUniform("camDist", TableCamera.DISTANCE)
        scope.drawRect(ShaderBrush(s))
    }

    /** Lamp brightness at plane point (u, v), matching the shader: used to light the tiles. */
    fun lampLight(u: Float, v: Float): Float {
        val du = u - LAMP_U
        val dv = (v - LAMP_V) * 0.85f
        return 0.12f + 0.95f * kotlin.math.exp(-(du * du + dv * dv) * LAMP_FALLOFF)
    }

    /** Strength of the lamp's glossy reflection at plane point (u, v), for tile glare. */
    fun lampGlare(u: Float, v: Float): Float {
        val mu = (u - LAMP_U) * 3.0f
        val mv = (v - (LAMP_V + GLARE_OFFSET_V)) * 1.6f
        return kotlin.math.exp(-(mu * mu + mv * mv) * 3.5f)
    }

    private val PLACEHOLDER: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.rgb(150, 125, 85))
    }

    // The lamp hangs over the middle of the table, slightly towards the far side
    private const val LAMP_U = 0f
    private const val LAMP_V = 0.06f
    private const val LAMP_FALLOFF = 2.6f
    // A glossy surface reflects the lamp a little beyond it, as seen from a tilted camera
    private const val GLARE_OFFSET_V = 0.2f
}

private const val TABLE_AGSL = """
uniform float2 res;
uniform float mode;     // 0 green felt mat, 1 walnut, 2 Thai straw mat (TableSurface order)
uniform float tilt;
uniform float camDist;
uniform shader matTex;  // graded photo of a woven sedge mat (mirrored tiling)
uniform float2 matSize;

const float PI = 3.14159265;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash11(float n) { return fract(sin(n * 12.9898) * 43758.5453); }

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

// ---- Walnut: narrow boards running away from the viewer, fine grain, glossy clearcoat ----
float3 walnut(float2 q, float fp) {
    float pw = 0.16;
    float plank = floor(q.x / pw + 0.5);
    float lx = q.x - plank * pw;
    float seed = hash11(plank + 3.0);
    // Ring position across the grain: mostly straight, gently and irregularly drifting
    float drift = (fbm(float2(lx * 6.0 + seed * 9.0, q.y * 0.6 + seed * 21.0)) - 0.5) * 2.4
                + (fbm(float2(lx * 2.0 + seed * 4.0, q.y * 0.18)) - 0.5) * 5.0;
    // Ring spacing varies across the board, from tight to open growth
    float density = 50.0 + 40.0 * vnoise(float2(lx * 5.0 + seed * 11.0, q.y * 0.15 + seed));
    float d = (lx + seed) * density + drift;
    float ring = fract(d);
    // Soft earlywood into a narrow, darker latewood line; uneven ring strength
    float late = smoothstep(0.7, 0.92, ring) * (1.0 - smoothstep(0.92, 1.0, ring));
    late *= 0.35 + 0.8 * vnoise(float2(floor(d) * 1.7, q.y * 0.9 + seed * 30.0));
    float3 light = float3(0.36, 0.22, 0.135);
    float3 dark = float3(0.13, 0.075, 0.048);
    float3 c = mix(light, dark, 0.3 + 0.6 * late);
    // Long heartwood streaks and broad figure, and per-board colour, so no two boards match
    float streak = fbm(float2(lx * 38.0 + seed * 7.0, q.y * 0.35 + seed * 3.0));
    c *= 0.66 + 0.62 * streak;
    c *= 0.86 + 0.24 * fbm(float2(lx * 9.0 + seed * 3.0, q.y * 1.2 + seed));
    c *= mix(float3(0.9, 0.94, 1.0), float3(1.1, 1.0, 0.9), seed);
    // Tiny open pores along the grain, faded out where they would alias
    float poreFade = 1.0 - smoothstep(0.0005, 0.0012, fp);
    float pore = smoothstep(0.82, 0.95, vnoise(float2(lx * 2200.0 + seed * 40.0, q.y * 160.0)));
    c *= 1.0 - 0.22 * pore * poreFade;
    // Fine seam between boards
    float seam = smoothstep(pw * 0.5 - fp * 2.5, pw * 0.5 - fp * 0.5, abs(lx));
    c *= 1.0 - 0.7 * seam;
    return c;
}

// ---- Green felt: a classic mahjong mat ----
float3 felt(float2 q, float fp) {
    float mott = fbm(q * 5.0);
    float3 c = mix(float3(0.035, 0.20, 0.15), float3(0.06, 0.29, 0.21), mott);
    float fine = 1.0 - smoothstep(0.0004, 0.0012, fp);
    float n1 = vnoise(q * 1500.0);
    float n2 = vnoise(q * 380.0 + 7.0);
    c *= 0.9 + 0.12 * n2 + 0.16 * (n1 - 0.5) * fine;
    // Stray lighter fibres lying in random directions
    float2 r = float2(q.x * 0.8 + q.y * 0.6, q.y * 0.8 - q.x * 0.6);
    float fib = smoothstep(0.88, 0.98, vnoise(float2(r.x * 2600.0, r.y * 600.0)));
    c += float3(0.03, 0.06, 0.05) * fib * fine;
    return c;
}

// ---- Thai straw mat: a real photograph of a woven sedge mat, graded to soft natural tones ----
float3 straw(float2 q) {
    // About 0.62 screen heights of mat per photo width: strands come out a few pixels wide
    float scale = matSize.x / 0.62;
    float2 t = float2(q.x * scale, -q.y * scale) + matSize * 0.5;
    return float3(matTex.eval(t).rgb) * 0.92;
}

half4 main(float2 p) {
    // Screen to tabletop plane through the tilted camera (inverse of TableCamera.planeToScreen)
    float2 s = float2((p.x - 0.5 * res.x) / res.y, -(p.y - 0.5 * res.y) / res.y);
    float ct = cos(tilt);
    float st = sin(tilt);
    float D = camDist;
    float v = s.y * D / (ct * D - s.y * st);
    float z = D + v * st;
    float u = s.x * z / D;
    float2 q = float2(u, v);
    // Approximate size of one screen pixel on the table, for anti-aliasing fine detail
    float fp = (z / D) / res.y * (1.0 + 0.6 * st);

    float3 albedo;
    float gloss;
    if (mode < 0.5) { albedo = felt(q, fp); gloss = 0.0; }
    else if (mode < 1.5) { albedo = walnut(q, fp); gloss = 1.0; }
    else { albedo = straw(q); gloss = 0.12; }

    // One warm lamp over the table; the room falls away into darkness
    float2 lq = (q - float2(0.0, 0.06)) * float2(1.0, 0.85);
    float pool = 0.12 + 0.95 * exp(-dot(lq, lq) * 2.6);
    float3 c = albedo * pool * float3(1.0, 0.93, 0.84);
    // Glossy surfaces reflect the lamp a little beyond it, stretched by the low viewing angle
    float2 m = (q - float2(0.0, 0.26)) * float2(3.4, 1.3);
    float glare = exp(-dot(m, m) * 5.0);
    float grain = mode > 0.5 && mode < 1.5 ? 0.75 + 0.5 * vnoise(q * float2(60.0, 6.0)) : 1.0;
    c += gloss * glare * grain * float3(0.24, 0.215, 0.18);
    // Screen-edge vignette deepens the dark corners
    float2 e = (p / res - 0.5) * float2(1.25, 1.0);
    c *= mix(0.55, 1.0, smoothstep(0.75, 0.2, length(e)));

    float ign = fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
    c += (ign - 0.5) / 255.0;
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""
