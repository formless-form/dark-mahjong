package com.vortessence.mahjong.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Menu background: dark slate lit by a low top-left lamp and, if [fire], the hearth off to the
 * left, flickering at 20 fps as on the table.
 *
 * Colour and normal map are baked once per size and kept for the app's life ([SlateMaps]), shared
 * by every menu screen; each frame only lights them (two image reads a pixel, so cheap).
 */
@Composable
fun SlateBackdrop(modifier: Modifier = Modifier, fire: Boolean = true, fireIntensity: Float = 0.5f, props: Int = MENU_PROPS) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val colourBake = remember { RuntimeShader(SLATE_AGSL).apply { setFloatUniform("bakeMap", 0f) } }
    val surfaceBake = remember { RuntimeShader(SLATE_AGSL).apply { setFloatUniform("bakeMap", 1f) } }
    val light = remember { RuntimeShader(LIGHT_AGSL) }
    // No GPU to bake on (JVM renders): computed whole, lamp only
    val direct = remember { RuntimeShader(SLATE_AGSL).apply { setFloatUniform("bakeMap", 2f) } }
    var bakeFailed by remember { mutableStateOf(false) }
    val colourLayer = rememberGraphicsLayer()
    val surfaceLayer = rememberGraphicsLayer()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var maps by remember { mutableStateOf<Pair<Bitmap, Bitmap>?>(null) }
    LaunchedEffect(size) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        // Baked on an earlier visit: drawn from the first frame (below)
        if (SlateMaps.get(size, density.density) != null) return@LaunchedEffect
        suspend fun bake(layer: GraphicsLayer, shader: RuntimeShader): Bitmap {
            shader.setFloatUniform("res", size.width.toFloat(), size.height.toFloat())
            shader.setFloatUniform("unit", density.density)
            layer.record(density, layoutDirection, size) { drawRect(ShaderBrush(shader)) }
            return layer.toImageBitmap().asAndroidBitmap()
        }
        // Baking needs the GPU (no software shaders, as in JVM previews); else drawn whole, lamp only
        maps = runCatching { bake(colourLayer, colourBake) to bake(surfaceLayer, surfaceBake) }.getOrNull()
        maps?.let { SlateMaps.keep(size, density.density, it) }
        bakeFailed = maps == null
    }
    val flameSeconds by produceState(0f, fire) {
        if (fire && MenuFire.flickers) {
            var last = Long.MIN_VALUE / 2
            while (true) withFrameNanos { now ->
                if (now - last >= MENU_FIRE_FRAME_NANOS) {
                    last = now
                    value = FrameClock.seconds(now)
                }
            }
        }
    }
    // Maps the light shader currently reads
    val bound = remember { arrayOfNulls<Pair<Bitmap, Bitmap>>(1) }
    Spacer(
        modifier
            .onSizeChanged { size = it }
            .drawBehind {
                // Baked now or kept from earlier; known from the first frame, as the size is
                val current = maps ?: SlateMaps.get(IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()), density.density)
                if (current != null && current !== bound[0]) {
                    light.setInputShader("colour", BitmapShader(current.first, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                    light.setInputShader("surface", BitmapShader(current.second, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                    bound[0] = current
                }
                // Baked slate, or computed whole where baking failed
                val shader = if (current != null) light else if (bakeFailed) direct else null
                if (shader == null) {
                    drawRect(Color(0xFF07090A))
                    return@drawBehind
                }
                shader.setFloatUniform("res", this.size.width, this.size.height)
                shader.setFloatUniform("props", props.toFloat())
                LampColour.setMenuUniform(shader)
                if (shader === direct) shader.setFloatUniform("unit", density.density)
                if (fire) {
                    val flame = FlameLight.at(flameSeconds, 0.5f, true, fireIntensity)
                    // Hearth off the left edge, its light moving with the flames
                    val w = this.size.width
                    shader.setFloatUniform(
                        "fireAt",
                        -0.3f * w + (flame.u - FlameLight.FIRE_U) * w * 2f,
                        this.size.height * 0.62f - (flame.v - FlameLight.FIRE_V) * w * 2f,
                        w * 0.32f * flame.flameHeight / 0.075f,
                    )
                    shader.setFloatUniform("fireColour", flame.r, flame.g, flame.b)
                    shader.setFloatUniform("firePower", MENU_FIRE * flame.power / flame.steadyPower)
                } else {
                    shader.setFloatUniform("fireAt", 0f, 0f, 1f)
                    shader.setFloatUniform("fireColour", 0f, 0f, 0f)
                    shader.setFloatUniform("firePower", 0f)
                }
                drawRect(ShaderBrush(shader))
            },
    )
}

/** Baked colour and surface for one size and density, kept for the app's life (~16 MB a phone). */
private object SlateMaps {
    private var size = IntSize.Zero
    private var density = 0f
    private var maps: Pair<Bitmap, Bitmap>? = null

    fun get(size: IntSize, density: Float): Pair<Bitmap, Bitmap>? =
        maps?.takeIf { (size == this.size) && (density == this.density) }

    fun keep(size: IntSize, density: Float, maps: Pair<Bitmap, Bitmap>) {
        this.size = size
        this.density = density
        this.maps = maps
    }
}

/** Menu firelight flicker: always in the app; still in JVM renders, which wait for the screen to settle. */
object MenuFire {
    @Volatile
    var flickers = true
}

/** The slate by faint firelight. */
@Composable
fun MenuBackdrop(fireIntensity: Float, fire: Boolean = true) {
    SlateBackdrop(Modifier.fillMaxSize(), fire = fire, fireIntensity = fireIntensity)
}

// Potted bamboo between hearth and stone (PROPS_AGSL; chosen 2026-10-30 over teapot and/or vase)
private const val MENU_PROPS = 1

// Share of the table's firelight (0.21 until 2026-10-30, raised so the bamboo's shadows show)
private const val MENU_FIRE = 0.5f

// 20 fps as on the table, less a little so 60 Hz frames land on it
private const val MENU_FIRE_FRAME_NANOS = 1_000_000_000L / 20 - 2_000_000L

/**
 * Slate height and colour at u (slate units), shared with the slate tabletop
 * (TableSurfaceRenderer). Prefixed apart from the tabletops' own, different noise.
 */
internal const val STONE_AGSL = """
// Dave Hoskins' "hash without sine"
float stoneHash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float stoneNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(mix(stoneHash(i), stoneHash(i + float2(1.0, 0.0)), u.x),
               mix(stoneHash(i + float2(0.0, 1.0)), stoneHash(i + float2(1.0, 1.0)), u.x), u.y);
}

// Six octaves, each rotated so no grid shows
float stoneFbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 6; i++) {
        v += a * stoneNoise(p);
        p = float2(1.6 * p.x + 1.2 * p.y, -1.2 * p.x + 1.6 * p.y) + 17.0;
        a *= 0.5;
    }
    return v;
}

// Three octaves: smooth wandering shapes
float stoneFbm3(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * stoneNoise(p);
        p = float2(1.6 * p.x + 1.2 * p.y, -1.2 * p.x + 1.6 * p.y) + 17.0;
        a *= 0.5;
    }
    return v / 0.875;
}

// Share of detail at [freq] per slate unit to keep when a pixel spans [lod] units: all at 4+ px a
// cycle, none below 2 (it would only sparkle). [lod] 0 keeps all (the menus, seen at one size)
float stoneKeep(float lod, float freq) {
    return lod > 0.0 ? smoothstep(0.5, 0.25, lod * freq) : 1.0;
}

// Crisp-edged cleavage terraces; up close, hairlines, flakes, grit and pits. Detail finer than
// [lod] shows is skipped (stoneKeep)
float stoneHeight(float2 u, float lod) {
    float2 s = float2(u.x * 0.55 + u.y * 0.12, u.y * 2.2);
    float layers = stoneFbm(s * 0.6);
    float steps = floor(layers * 7.0) / 7.0 + smoothstep(0.0, 0.12, fract(layers * 7.0)) / 7.0;
    // Hairline cleavage: fine stretched ridges, two sizes
    float2 c = float2(u.x * 3.0 + u.y * 0.6, u.y * 26.0);
    float hair = 1.0 - abs(stoneNoise(c + stoneFbm3(u * 2.0) * 3.0) * 2.0 - 1.0);
    float keepHair2 = stoneKeep(lod, 60.0);
    float hair2 = keepHair2 > 0.0 ? (1.0 - abs(stoneNoise(c * 2.3 + 11.0 + stoneFbm3(u * 4.0) * 3.0) * 2.0 - 1.0)) * keepHair2 : 0.0;
    // Flakes: partly lifted thin sheets
    float flake = smoothstep(0.62, 0.7, stoneNoise(u * float2(9.0, 22.0) + 3.0)) * 0.6;
    // Grit in three sizes, and the odd pit
    float keep55 = stoneKeep(lod, 55.0);
    float keep120 = stoneKeep(lod, 120.0);
    float keep260 = stoneKeep(lod, 260.0);
    float grit = 0.0;
    if (keep55 > 0.0) grit += stoneNoise(u * 55.0) * 0.5 * keep55;
    if (keep120 > 0.0) grit += stoneNoise(u * 120.0) * 0.3 * keep120;
    if (keep260 > 0.0) grit += stoneNoise(u * 260.0) * 0.2 * keep260;
    // Skipped detail replaced by its average, so the height doesn't shift
    grit += 0.5 * (0.5 * (1.0 - keep55) + 0.3 * (1.0 - keep120) + 0.2 * (1.0 - keep260));
    float pit = smoothstep(0.93, 0.98, stoneNoise(u * 38.0 + 7.0)) * stoneKeep(lod, 38.0);
    return steps * 1.3 + stoneFbm(u * 3.0) * 0.22 + hair * 0.05 + hair2 * 0.025 + flake * 0.03
        + grit * 0.03 - pit * 0.05;
}

float3 stoneColour(float2 u) {
    float tone = stoneFbm(u * 0.35 + 4.0);
    float3 c = mix(float3(0.045, 0.053, 0.057), float3(0.125, 0.137, 0.143), tone);
    // Rusty stains along a few layers
    c = mix(c, float3(0.12, 0.09, 0.07), smoothstep(0.62, 0.8, stoneFbm(u * float2(0.4, 1.6) + 9.0)) * 0.3);
    // Mottling and mica specks
    c *= 0.82 + 0.3 * stoneNoise(u * 70.0) + 0.1 * stoneFbm(u * 18.0);
    c += step(0.992, stoneHash(floor(u * 160.0))) * 0.05;
    return c;
}

// Normal at u from the slope over e (~1 px): x, y as u's, z up, relief softened as in the menus
float3 stoneNormal(float2 u, float e, float lod) {
    float h = stoneHeight(u, lod);
    return normalize(float3(h - stoneHeight(u + float2(e, 0.0), lod), h - stoneHeight(u + float2(0.0, e), lod), e * 3.5));
}
"""

/**
 * The hearth-side bamboo's silhouette (PROPS_AGSL) as signed distances, negative inside. Left off
 * the game table (tried 2026-10-30): too distracting.
 */
private const val SILHOUETTES_AGSL = """
float sdEllipse(float2 p, float2 r) {
    return (length(p / r) - 1.0) * min(r.x, r.y);
}
float sdCapsule(float2 p, float2 a, float2 b, float r) {
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - r;
}
float sdBox(float2 p, float2 b) {
    float2 d = abs(p) - b;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}
// Narrow blade from at, angle ang (radians, 0 along +s), len long
float sdLeaf(float2 q, float2 at, float len, float ang) {
    float2 v = q - at;
    float c = cos(ang);
    float s = sin(ang);
    float2 r = float2(c * v.x + s * v.y, -s * v.x + c * v.y) - float2(len * 0.5, 0.0);
    return sdEllipse(r, float2(len * 0.5, len * 0.085));
}

// In units of its height: s across, z up from its foot
float bamboo(float2 q) {
    float d = sdBox(q - float2(0.02, 0.07), float2(0.17, 0.07));
    d = min(d, sdCapsule(q, float2(-0.06, 0.1), float2(-0.13, 1.0), 0.02));
    d = min(d, sdCapsule(q, float2(0.03, 0.1), float2(0.09, 0.86), 0.018));
    d = min(d, sdCapsule(q, float2(0.10, 0.1), float2(0.27, 0.66), 0.016));
    // Drooping leaves from the upper joints
    d = min(d, sdLeaf(q, float2(-0.11, 0.80), 0.30, 2.7));
    d = min(d, sdLeaf(q, float2(-0.12, 0.92), 0.26, 0.5));
    d = min(d, sdLeaf(q, float2(-0.13, 0.98), 0.22, 2.0));
    d = min(d, sdLeaf(q, float2(-0.09, 0.62), 0.28, -0.3));
    d = min(d, sdLeaf(q, float2(0.07, 0.72), 0.30, 0.25));
    d = min(d, sdLeaf(q, float2(0.08, 0.80), 0.24, 1.2));
    d = min(d, sdLeaf(q, float2(0.09, 0.84), 0.26, 2.9));
    d = min(d, sdLeaf(q, float2(0.20, 0.52), 0.30, 0.6));
    d = min(d, sdLeaf(q, float2(0.25, 0.62), 0.22, -0.2));
    d = min(d, sdLeaf(q, float2(0.24, 0.64), 0.20, 1.9));
    return d;
}
"""

/**
 * Potted bamboo just off the left edge, a cut-out facing the fire: propsLight(p) is the fire
 * reaching p past it (props 1; 0 omits it). The low, broad fire casts a long shadow that softens
 * with distance and sways with the flames.
 */
private const val PROPS_AGSL = """
$SILHOUETTES_AGSL
// Fire reaching p past the bamboo at o, h px tall
float propLight(float2 p, float2 o, float h) {
    float2 nrm = normalize(fireAt.xy - o);
    float2 tng = float2(-nrm.y, nrm.x);
    // Where p's line to the fire passes it (0 at p, 1 at the fire)
    float t = dot(o - p, nrm) / dot(fireAt.xy - p, nrm);
    if (t <= 0.0 || t >= 1.0) return 1.0;
    float2 x = p + (fireAt.xy - p) * t;
    float2 q = float2(dot(x - o, tng), fireAt.z * t) / h;
    // Broad flames: softer further beyond it
    float soft = res.x * 0.035 * t / h + 0.004;
    return smoothstep(-soft, soft, bamboo(q));
}

float propsLight(float2 p) {
    if (props < 0.5) return 1.0;
    // Halfway between the screen edge and the hearth (-0.3 w, 0.62 h)
    return propLight(p, float2(-0.15 * res.x, res.y * 0.53), 0.75 * res.x);
}
"""

/**
 * Lamp and fire on slate point p (px) of colour a facing n; fireAt is x, y in px and z the height,
 * with the props' long shadows (PROPS_AGSL).
 */
private const val MENU_LIGHT_AGSL = """
uniform float2 res;
uniform float3 fireAt;
uniform float3 fireColour;
uniform float firePower;
uniform float props;
// As at the table (LampColour)
uniform float3 lampColour;
$PROPS_AGSL
half4 menuLight(float3 a, float3 n, float2 p) {
    float3 view = float3(0.0, 0.0, 1.0);

    // Lamp: low, top left
    float3 l = normalize(float3(-0.55, -0.7, 0.45));
    float lamp = 0.5 + 0.7 * max(dot(n, l), 0.0);
    float lampGlint = pow(max(dot(n, normalize(l + view)), 0.0), 40.0) * 0.05;
    float2 q = p / res;
    float sheen = exp(-dot(q - float2(0.25, 0.15), q - float2(0.25, 0.15)) * 3.0) * 0.03;
    float3 c = (a * lamp + (lampGlint + sheen) * 0.72) * lampColour;

    // Fire: low and to the side, raking across the stone's edges, past the props
    float3 toFire = float3(fireAt.xy - p, fireAt.z);
    float d = length(toFire);
    float3 f = toFire / d;
    float fall = 1.0 / (1.0 + (d / res.x) * (d / res.x) * 2.5);
    float rake = max(dot(n, f), 0.0);
    float glint = pow(max(dot(n, normalize(f + view)), 0.0), 30.0);
    c += fireColour * firePower * fall * (a * rake * 9.0 + glint * 0.35) * propsLight(p);

    // Vignette
    c *= 1.0 - 0.35 * dot(q - 0.5, q - 0.5);
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

/**
 * Bakes the slate's colour (bakeMap 0) or normal as 0.5 + 0.5 n (bakeMap 1). bakeMap 2: both,
 * lamp-lit as LIGHT_AGSL does, where nothing can be baked.
 */
private const val SLATE_AGSL = """
uniform float unit;   // pixels per dp
uniform float bakeMap;
$STONE_AGSL
$MENU_LIGHT_AGSL
// Darker than the stone itself, so text stands out
float3 colour(float2 u) {
    return stoneColour(u) * 0.72;
}

half4 main(float2 p) {
    // 140 dp a slate unit: close up, so layers read large
    float scale = 140.0;
    float2 u = p / (unit * scale);
    if (bakeMap < 0.5) return half4(half3(colour(u)), 1.0);
    // Slope over one pixel, so even the finest grain catches the light
    float3 n = stoneNormal(u, 1.0 / (unit * scale), 0.0);
    if (bakeMap < 1.5) return half4(half3(n * 0.5 + 0.5), 1.0);
    return menuLight(colour(u), n, p);
}
"""

/** Lights the baked slate each frame. */
private const val LIGHT_AGSL = """
uniform shader colour;
uniform shader surface;
$MENU_LIGHT_AGSL
half4 main(float2 p) {
    return menuLight(colour.eval(p).rgb, normalize(surface.eval(p).rgb * 2.0 - 1.0), p);
}
"""
