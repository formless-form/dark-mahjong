package com.vortessence.mahjong.ui.components

import android.content.Context
import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.vortessence.mahjong.data.model.TableLighting
import com.vortessence.mahjong.data.model.TableSurface
import kotlin.math.exp
import kotlin.math.sin

/**
 * Draws the tabletop as one full-screen AGSL shader: procedural green felt, walnut, or Thai straw
 * mat (redrawn from measurements of a real woven sedge mat), seen through [TableCamera]'s tilted perspective and lit by a single warm lamp above the
 * middle of the table, with the room falling away into darkness around it.
 */
object TableSurfaceRenderer {

    private var shader: RuntimeShader? = null
    private var candleShader: RuntimeShader? = null

    /**
     * Draws the table over [width] x [height]. When the player has zoomed in, the caller draws
     * this under the zoom transform and passes the same [zoom] and [pan], so fine detail is
     * sharpened to match while the vignette and dither stay fixed to the screen.
     */
    @Suppress("UNUSED_PARAMETER")
    fun drawTableSurface(
        scope: DrawScope,
        surface: TableSurface,
        width: Float,
        height: Float,
        context: Context? = null,
        zoom: Float = 1f,
        pan: Offset = Offset.Zero,
        lighting: TableLighting = TableLighting.LAMP,
        flicker: Float = 1f,
    ) {
        val s = shader ?: RuntimeShader(TABLE_AGSL).also { shader = it }
        s.setFloatUniform("res", width, height)
        s.setFloatUniform("mode", surface.ordinal.toFloat())
        s.setFloatUniform("tilt", Math.toRadians(TableCamera.TILT_DEGREES.toDouble()).toFloat())
        s.setFloatUniform("camDist", TableCamera.DISTANCE)
        s.setFloatUniform("zoom", zoom)
        s.setFloatUniform("pan", pan.x, pan.y)
        s.setFloatUniform("lighting", lighting.ordinal.toFloat())
        s.setFloatUniform("flicker", flicker)
        scope.drawRect(ShaderBrush(s))
    }

    /**
     * Candlelight for the tiles in flat table pixels (as [MahjongBoard]'s lamp brush): drawn with
     * BlendMode.Modulate over everything on the table, matching the table's own candle pools.
     */
    fun candleTileLight(width: Float, height: Float, flicker: Float): ShaderBrush {
        val s = candleShader ?: RuntimeShader(CANDLE_TILE_AGSL).also { candleShader = it }
        s.setFloatUniform("res", width, height)
        s.setFloatUniform("flicker", flicker)
        return ShaderBrush(s)
    }

    /**
     * The candle's brightness at [seconds], about 1: a slow sway with a quicker shiver, each only a
     * few percent, so the light breathes rather than strobes.
     */
    fun candleFlicker(seconds: Float): Float {
        val sway = 0.5f * sin(seconds * 1.7f) + 0.5f * sin(seconds * 2.9f)
        val shiver = sin(seconds * 11.3f) * sin(seconds * 17.9f)
        return 1f + 0.035f * sway + 0.025f * shiver
    }

    /** Strength of the lamp's and candles' glossy reflection at plane point (u, v), for tile glare. */
    fun candleGlare(u: Float, v: Float, flicker: Float): Float {
        val mu = (u - CANDLE_U) * 3.4f
        val mv = (v - CANDLE_V + 0.05f) * 2.2f
        // The dimmed lamp's own reflection, plus a fainter one of the flame
        return 0.75f * lampGlare(u, v) + 0.5f * flicker * exp(-(mu * mu + mv * mv) * 4f)
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

    // The lamp hangs over the middle of the table, slightly towards the far side
    private const val LAMP_U = 0f
    private const val LAMP_V = 0.06f
    private const val LAMP_FALLOFF = 2.6f
    // A glossy surface reflects the lamp a little beyond it, as seen from a tilted camera
    private const val GLARE_OFFSET_V = 0.2f

    // A candle stands on the table just out of view, past the top-left of the board; the
    // shaders below place it at the same point
    private const val CANDLE_U = -0.30f
    private const val CANDLE_V = 0.56f
}

/**
 * Candlelight at plane point q (in table heights, v up), shared by both shaders: one low flame
 * just out of view to the top left, a soft inverse-square pool scaled by its flicker, in flame
 * colour (about 1900 K). It is added to a dimmed lamp rather than replacing it: the lamp
 * overhead keeps every tile face readable, wherever the candle's light falls short.
 */
private const val CANDLE_AGSL = """
const float CANDLE_LAMP = 0.75; // the lamp's strength when the candles are lit
const float2 CANDLE = float2(-0.30, 0.56);
float3 candles(float2 q, float flicker) {
    float2 d = q - CANDLE;
    return flicker * 0.7 / (1.0 + dot(d, d) / 0.09) * float3(1.0, 0.62, 0.30);
}
"""

private const val CANDLE_TILE_AGSL = """
uniform float2 res;
uniform float flicker;
$CANDLE_AGSL
half4 main(float2 p) {
    float2 q = float2((p.x - 0.5 * res.x) / res.y, (0.5 * res.y - p.y) / res.y);
    // The lamp brush's own falloff (lampLightBrush), dimmed, plus the candles; times the room's exposure
    float2 l = (q - float2(0.0, 0.06)) / 0.95;
    float lamp = 0.30 + 0.70 * exp(-dot(l, l) * 3.2);
    float3 c = (CANDLE_LAMP * lamp * float3(1.0, 0.95, 0.88) + candles(q, flicker)) * 0.81;
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

private const val TABLE_AGSL = """
uniform float2 res;
uniform float mode;     // 0 green felt mat, 1 walnut, 2 Thai straw mat (TableSurface order)
uniform float tilt;
uniform float camDist;
uniform float zoom;     // the player's pinch zoom, drawn under the matching canvas transform
uniform float2 pan;     // screen position = p * zoom + pan
uniform float lighting; // 0 lamp, 1 candles (TableLighting order)
uniform float flicker;  // the candle's brightness this frame, about 1
$CANDLE_AGSL

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
    // Grain runs strictly along the board, parallel to its edges, so (like the tile columns)
    // every line converges with the perspective. Ring spacing varies across the board, from
    // tight to open growth, but stays constant along it; only a tiny natural waver remains
    float density = 50.0 + 40.0 * vnoise(float2(lx * 5.0 + seed * 11.0, seed * 3.0));
    float d = (lx + seed) * density + 0.16 * (vnoise(float2(lx * 40.0 + seed, q.y * 1.2)) - 0.5);
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

// ---- Thai straw mat: a woven sedge mat, redrawn procedurally from measurements of a real one
// (strand and binding-thread spacing, palette, motif bands), lying flat and square to the tiles ----
float3 matColour(float id) {
    if (id < 0.5) return float3(0.70, 0.62, 0.45);   // natural straw
    if (id < 1.5) return float3(0.33, 0.44, 0.36);   // muted jade
    return float3(0.56, 0.37, 0.34);                  // dusky rose
}

// Which colour a strand shows in a given row of the weave: bands of straw/jade stripes, rose
// separators, and rose bands with stepped straw and jade blocks, as in the original mat
float matPattern(float col, float row) {
    float band = mod(row, 9.0);
    // Stripe runs across the strands: 4 straw, 3 jade, 5 straw, 2 jade, 3 straw, 4 jade
    float k = mod(col, 21.0);
    float stripe = (k < 4.0 || (k >= 7.0 && k < 12.0) || (k >= 14.0 && k < 17.0)) ? 0.0 : 1.0;
    if (band < 2.0 || (band > 5.5 && band < 7.5)) return stripe;
    if (band < 2.5 || band > 7.5) return 2.0;
    // Motif rows: rose ground with straw and jade blocks stepping diagonally row to row
    float m = mod(col + row * 3.0, 15.0);
    if (m < 5.0) return 0.0;
    if (m >= 8.0 && m < 10.0) return 1.0;
    return 2.0;
}

float3 straw(float2 q, float fp) {
    float sw = 0.0034;               // strand pitch (screen heights)
    float L = sw * 11.5;             // spindle length = binding-thread spacing, as measured
    float col = floor(q.x / sw);
    float a = fract(q.x / sw) - 0.5; // -0.5..0.5 across the strand
    // Neighbouring strands are staggered a little, so pointed tips interleave where colours
    // change while the rows still run straight across, square to the tiles
    float off = (hash11(col * 1.37 + 5.0) - 0.5) * 0.24 * L;
    float along = (q.y + off) / L;
    float row = floor(along);
    float t = fract(along);          // 0..1 along this spindle
    float shade = 1.0 - smoothstep(0.0, max(fp / sw * 1.5, 0.08), abs(a) - 0.33);
    // Spindle outline: full width for most of its length, tapering to points near its ends;
    // the reeds pack tightly side by side
    float halfW = 0.5 * pow(sin(3.14159265 * t), 0.2);
    float inside = 1.0 - smoothstep(halfW - max(fp / sw, 0.06), halfW, abs(a));

    float3 c = matColour(matPattern(col, row));
    c *= 0.9 + 0.18 * hash21(float2(col, row));           // each reed a little different
    // Rounded reed: lit along its crown, darker at its sides; fibre streaks along its length
    float r = clamp(abs(a) / max(halfW, 0.01), 0.0, 1.0);
    c *= 0.72 + 0.28 * sqrt(1.0 - r * r);
    float fine = 1.0 - smoothstep(0.0005, 0.0014, fp);
    c *= 0.92 + 0.12 * vnoise(float2(a * 9.0 + col * 3.7, q.y / sw * 0.9)) * fine;
    c += float3(0.05, 0.045, 0.035) * smoothstep(0.08, 0.0, abs(a)) * (1.0 - r) * fine;

    // Years of use: broad scuffed, sun-faded patches where colour bleaches and reeds flatten
    float wear = smoothstep(0.42, 0.78, fbm(q * 2.6 + 17.0));
    float lumC = dot(c, float3(0.299, 0.587, 0.114));
    float3 faded = mix(float3(lumC), float3(0.72, 0.66, 0.52) * (lumC + 0.12), 0.5);
    c = mix(c, faded, 0.18 + 0.45 * wear);
    c *= 1.0 + 0.1 * wear * (1.0 - sqrt(1.0 - r * r));
    // The odd broken, frayed reed: lighter and fibrous along part of its length
    float broken = step(0.965, hash21(float2(col * 0.7 + 2.0, row + 9.0)));
    if (broken > 0.5) {
        float frayAt = 0.25 + 0.5 * hash21(float2(col, row * 1.3));
        float fray = 1.0 - smoothstep(0.0, 0.22, abs(t - frayAt));
        c = mix(c, float3(0.66, 0.6, 0.47), fray * 0.55);
        inside *= 1.0 - fray * 0.6 * step(0.5, vnoise(float2(a * 24.0, t * 60.0)));
    }

    // Between and behind the reeds: dark, slightly grimy gaps, with pale binding-thread ticks
    float3 gap = float3(0.085, 0.06, 0.045) * (0.85 + 0.3 * vnoise(q * 90.0));
    // The binding thread crosses where the reeds' tips meet, glimpsed only between the tips
    float tip = abs(t - floor(t + 0.5));
    float thread = 1.0 - smoothstep(0.012, 0.03, tip);
    gap = mix(gap, float3(0.55, 0.5, 0.4), thread * 0.5 * fine);
    float3 m = mix(gap, c, inside * (0.55 + 0.45 * shade));

    // Faint old stains (tea, water) with slightly darker tide-marks at their rims
    float st = fbm(q * 1.4 + 41.0);
    float stain = smoothstep(0.6, 0.66, st);
    float rim = smoothstep(0.58, 0.61, st) * (1.0 - smoothstep(0.61, 0.66, st));
    m *= 1.0 - 0.1 * stain - 0.12 * rim;
    m = mix(m, m * float3(1.0, 0.94, 0.84), stain * 0.6);

    // A few loose fibres lying across the weave
    float2 cellF = floor(q / 0.09);
    float2 lf = fract(q / 0.09) - 0.5;
    float pick = hash21(cellF + 3.3);
    if (pick > 0.72) {
        float ang = hash21(cellF + 7.1) * 3.14159265;
        float2 dir = float2(cos(ang), sin(ang));
        float along2 = dot(lf, dir);
        float dist = abs(dot(lf, float2(-dir.y, dir.x)) + 0.04 * sin(along2 * 9.0 + pick * 20.0));
        float len = 0.18 + 0.2 * hash21(cellF + 1.9);
        float fibre = (1.0 - smoothstep(0.0, max(0.006, fp / 0.09 * 1.2), dist)) * step(abs(along2), len);
        m = mix(m, float3(0.68, 0.62, 0.48), fibre * 0.7 * fine);
    }
    return m;
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
    float fp = (z / D) / res.y * (1.0 + 0.6 * st) / zoom;
    // The real screen pixel, for effects that belong to the screen rather than the table
    float2 sp = p * zoom + pan;
    float3 albedo;
    float gloss;
    if (mode < 0.5) { albedo = felt(q, fp); gloss = 0.0; }
    else if (mode < 1.5) { albedo = walnut(q, fp); gloss = 1.0; }
    else { albedo = straw(q, fp); gloss = 0.1; }

    // One warm lamp over the table; the room falls away into darkness
    float2 lq = (q - float2(0.0, 0.06)) * float2(1.0, 0.85);
    float pool = 0.12 + 0.95 * exp(-dot(lq, lq) * 2.6);
    float3 c = albedo * pool * float3(1.0, 0.93, 0.84);
    // Glossy surfaces reflect the lamp a little beyond it, stretched by the low viewing angle
    float2 m = (q - float2(0.0, 0.26)) * float2(3.4, 1.3);
    float glare = exp(-dot(m, m) * 5.0);
    if (lighting > 0.5) {
        // Or the lamp turned down, with a candle standing on the table, reflected just in front
        // of its flame
        c = albedo * (CANDLE_LAMP * pool * float3(1.0, 0.93, 0.84) + candles(q, flicker));
        float2 mc = (q - CANDLE + float2(0.0, 0.05)) * float2(3.4, 2.2);
        c += gloss * 0.6 * flicker * exp(-dot(mc, mc) * 4.0) * float3(0.26, 0.15, 0.06);
        glare *= CANDLE_LAMP;
    }
    float grain = mode > 0.5 && mode < 1.5 ? 0.75 + 0.5 * vnoise(q * float2(60.0, 6.0)) : 1.0;
    c += gloss * glare * grain * float3(0.24, 0.215, 0.18);
    // Screen-edge vignette deepens the dark corners
    float2 e = (sp / res - 0.5) * float2(1.25, 1.0);
    c *= mix(0.55, 1.0, smoothstep(0.75, 0.2, length(e)));
    // Overall exposure: the room is dark ("Dark Mahjong"); shared with the tiles' lamp light
    c *= 0.9;

    float ign = fract(52.9829189 * fract(dot(floor(sp), float2(0.06711056, 0.00583715))));
    c += (ign - 0.5) / 255.0;
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""
