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
 * The menu screens' background: dark slate, by the light of a low lamp at the top left and,
 * if [fire], the hearth off to the left, flickering 20 times a second as on the table.
 *
 * The slate is worked out once for each size, into two images: its colour, and the way its
 * surface faces at every pixel (a normal map) — the terraces where its layers split, hairline
 * cleavage lines, grit and pits. Each frame only lights those: the lamp, and the fire from where
 * it burns this moment, so ridges facing the hearth catch it and flicker while those facing away
 * stay dark, with a faint glint along the edges. Two image reads a pixel, so cheap.
 *
 * The two images are kept for the app's life ([SlateMaps]) and shared by every menu screen, so
 * only the first visit bakes them.
 */
@Composable
fun SlateBackdrop(modifier: Modifier = Modifier, fire: Boolean = true, fireIntensity: Float = 0.5f) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val colourBake = remember { RuntimeShader(SLATE_AGSL).apply { setFloatUniform("bakeMap", 0f) } }
    val surfaceBake = remember { RuntimeShader(SLATE_AGSL).apply { setFloatUniform("bakeMap", 1f) } }
    val light = remember { RuntimeShader(LIGHT_AGSL) }
    // Without a GPU to bake on (the JVM renders), the slate lit by the lamp alone, worked out whole
    val direct = remember { RuntimeShader(SLATE_AGSL).apply { setFloatUniform("bakeMap", 2f) } }
    var bakeFailed by remember { mutableStateOf(false) }
    val colourLayer = rememberGraphicsLayer()
    val surfaceLayer = rememberGraphicsLayer()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var maps by remember { mutableStateOf<Pair<Bitmap, Bitmap>?>(null) }
    LaunchedEffect(size) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        // Already baked on an earlier visit: drawn from the first frame (see below)
        if (SlateMaps.get(size, density.density) != null) return@LaunchedEffect
        suspend fun bake(layer: GraphicsLayer, shader: RuntimeShader): Bitmap {
            shader.setFloatUniform("res", size.width.toFloat(), size.height.toFloat())
            shader.setFloatUniform("unit", density.density)
            layer.record(density, layoutDirection, size) { drawRect(ShaderBrush(shader)) }
            return layer.toImageBitmap().asAndroidBitmap()
        }
        // Baking needs the GPU (shaders can't be drawn in software, as in the JVM previews):
        // without it, the slate is drawn whole each time, by the lamp alone
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
                    value = now / 1e9f
                }
            }
        }
    }
    // The maps the light shader reads now
    val bound = remember { arrayOfNulls<Pair<Bitmap, Bitmap>>(1) }
    Spacer(
        modifier
            .onSizeChanged { size = it }
            .drawBehind {
                // Baked on this visit, or kept from an earlier one: that is known from the first
                // frame, as the screen's size is
                val current = maps ?: SlateMaps.get(IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()), density.density)
                if (current != null && current !== bound[0]) {
                    light.setInputShader("colour", BitmapShader(current.first, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                    light.setInputShader("surface", BitmapShader(current.second, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                    bound[0] = current
                }
                if (current == null) {
                    if (bakeFailed) {
                        direct.setFloatUniform("res", this.size.width, this.size.height)
                        direct.setFloatUniform("unit", density.density)
                        drawRect(ShaderBrush(direct))
                    } else {
                        drawRect(Color(0xFF07090A))
                    }
                    return@drawBehind
                }
                light.setFloatUniform("res", this.size.width, this.size.height)
                if (fire) {
                    val flame = FlameLight.at(flameSeconds, 0.5f, true, fireIntensity)
                    // The hearth off the left edge, its light moving and leaping with the flames
                    val w = this.size.width
                    light.setFloatUniform(
                        "fireAt",
                        -0.3f * w + (flame.u - FlameLight.FIRE_U) * w * 2f,
                        this.size.height * 0.62f - (flame.v - FlameLight.FIRE_V) * w * 2f,
                        w * 0.32f * flame.flameHeight / 0.075f,
                    )
                    light.setFloatUniform("fireColour", flame.r, flame.g, flame.b)
                    light.setFloatUniform("firePower", MENU_FIRE * flame.power / flame.steadyPower)
                } else {
                    light.setFloatUniform("fireAt", 0f, 0f, 1f)
                    light.setFloatUniform("fireColour", 0f, 0f, 0f)
                    light.setFloatUniform("firePower", 0f)
                }
                drawRect(ShaderBrush(light))
            },
    )
}

/**
 * The slate's baked colour and surface, kept for the app's life once made (some 16 MB for a
 * phone's screen), for the screen size and density they were made for.
 */
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

/** Whether the menu firelight flickers: always in the app; held still in JVM renders, which wait for the screen to settle. */
object MenuFire {
    @Volatile
    var flickers = true
}

/** The menu screens' background: the slate, by the faint firelight. */
@Composable
fun MenuBackdrop(fireIntensity: Float) {
    SlateBackdrop(Modifier.fillMaxSize(), fireIntensity = fireIntensity)
}

// The menu's firelight, the same on every menu screen: a faint share of the table's (tuned by
// eye, 2026-10-30)
private const val MENU_FIRE = 0.21f

// 20 frames a second, as on the table, less a little so a 60 Hz screen's frames land on it
private const val MENU_FIRE_FRAME_NANOS = 1_000_000_000L / 20 - 2_000_000L

/**
 * The slate, baked once: its colour (bakeMap 0), or its surface (bakeMap 1: the normal, as
 * 0.5 + 0.5 n, in rgb). bakeMap 2: both at once, lit by the lamp alone as LIGHT_AGSL lights them,
 * for where nothing can be baked.
 */
private const val SLATE_AGSL = """
uniform float2 res;
uniform float unit;   // pixels per dp
uniform float bakeMap;

// Dave Hoskins' "hash without sine"
float hash21(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), u.x),
               mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), u.x), u.y);
}

// Six octaves, each turned a little so no grid shows
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 6; i++) {
        v += a * vnoise(p);
        p = float2(1.6 * p.x + 1.2 * p.y, -1.2 * p.x + 1.6 * p.y) + 17.0;
        a *= 0.5;
    }
    return v;
}

// Three octaves only: smooth, for shapes that wander without crinkling
float fbm3(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * vnoise(p);
        p = float2(1.6 * p.x + 1.2 * p.y, -1.2 * p.x + 1.6 * p.y) + 17.0;
        a *= 0.5;
    }
    return v / 0.875;
}

// Thin layers split along their cleavage, so the face is stepped in terraces with crisp edges;
// up close it is fine-grained, with hairline cleavage lines, flakes, grit and pits
float height(float2 u) {
    float2 s = float2(u.x * 0.55 + u.y * 0.12, u.y * 2.2);
    float layers = fbm(s * 0.6);
    float steps = floor(layers * 7.0) / 7.0 + smoothstep(0.0, 0.12, fract(layers * 7.0)) / 7.0;
    // Hairline cleavage: very fine, stretched ridges along the layers, two sizes
    float2 c = float2(u.x * 3.0 + u.y * 0.6, u.y * 26.0);
    float hair = 1.0 - abs(vnoise(c + fbm3(u * 2.0) * 3.0) * 2.0 - 1.0);
    float hair2 = 1.0 - abs(vnoise(c * 2.3 + 11.0 + fbm3(u * 4.0) * 3.0) * 2.0 - 1.0);
    // Flakes: small raised scales where a thin sheet has partly lifted
    float flake = smoothstep(0.62, 0.7, vnoise(u * float2(9.0, 22.0) + 3.0)) * 0.6;
    // Grit: a dense, fine grain in three sizes, and the odd pit
    float grit = vnoise(u * 55.0) * 0.5 + vnoise(u * 120.0) * 0.3 + vnoise(u * 260.0) * 0.2;
    float pit = smoothstep(0.93, 0.98, vnoise(u * 38.0 + 7.0));
    return steps * 1.3 + fbm(u * 3.0) * 0.22 + hair * 0.05 + hair2 * 0.025 + flake * 0.03
        + grit * 0.03 - pit * 0.05;
}

float3 colour(float2 u) {
    float tone = fbm(u * 0.35 + 4.0);
    float3 c = mix(float3(0.045, 0.053, 0.057), float3(0.125, 0.137, 0.143), tone);
    // Rusty mineral stains along a few layers
    c = mix(c, float3(0.12, 0.09, 0.07), smoothstep(0.62, 0.8, fbm(u * float2(0.4, 1.6) + 9.0)) * 0.3);
    // Fine mottling, and pale specks of mica in the grain
    c *= 0.82 + 0.3 * vnoise(u * 70.0) + 0.1 * fbm(u * 18.0);
    c += step(0.992, hash21(floor(u * 160.0))) * 0.05;
    // A little darker all over, so the text on it stands out
    return c * 0.72;
}

half4 main(float2 p) {
    // One slate unit is 140 dp: close up, so its layers and lines read large
    float scale = 140.0;
    float2 u = p / (unit * scale);
    if (bakeMap < 0.5) return half4(half3(colour(u)), 1.0);
    // The surface's slope over one pixel, so even the finest grain catches the light
    float e = 1.0 / (unit * scale);
    float h = height(u);
    float3 n = normalize(float3(h - height(u + float2(e, 0.0)), h - height(u + float2(0.0, e)), e * 3.5));
    if (bakeMap < 1.5) return half4(half3(n * 0.5 + 0.5), 1.0);
    float3 l = normalize(float3(-0.55, -0.7, 0.45));
    float3 c = colour(u) * (0.5 + 0.7 * max(dot(n, l), 0.0));
    c += pow(max(dot(n, normalize(l + float3(0.0, 0.0, 1.0))), 0.0), 40.0) * 0.05 * 0.72;
    float2 q = p / res;
    c += exp(-dot(q - float2(0.25, 0.15), q - float2(0.25, 0.15)) * 3.0) * 0.03 * 0.72;
    c *= 1.0 - 0.35 * dot(q - 0.5, q - 0.5);
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

/**
 * Lights the baked slate, every frame: a low lamp at the top left (with its broad, soft sheen),
 * and the fire at fireAt (x, y in pixels; z its height above the stone), each lighting the faces
 * turned towards it, with a faint glint where an edge reflects it. Then a gentle vignette.
 */
private const val LIGHT_AGSL = """
uniform shader colour;
uniform shader surface;
uniform float2 res;
uniform float3 fireAt;
uniform float3 fireColour;
uniform float firePower;

half4 main(float2 p) {
    float3 a = colour.eval(p).rgb;
    float3 n = normalize(surface.eval(p).rgb * 2.0 - 1.0);
    float3 view = float3(0.0, 0.0, 1.0);

    // The lamp: low, from the top left
    float3 l = normalize(float3(-0.55, -0.7, 0.45));
    float lamp = 0.5 + 0.7 * max(dot(n, l), 0.0);
    float lampGlint = pow(max(dot(n, normalize(l + view)), 0.0), 40.0) * 0.05;
    float2 q = p / res;
    float sheen = exp(-dot(q - float2(0.25, 0.15), q - float2(0.25, 0.15)) * 3.0) * 0.03;
    float3 c = a * lamp + (lampGlint + sheen) * 0.72;

    // The fire: low and to the side, so it rakes across the stone and finds its edges
    float3 toFire = float3(fireAt.xy - p, fireAt.z);
    float d = length(toFire);
    float3 f = toFire / d;
    float fall = 1.0 / (1.0 + (d / res.x) * (d / res.x) * 2.5);
    float rake = max(dot(n, f), 0.0);
    float glint = pow(max(dot(n, normalize(f + view)), 0.0), 30.0);
    c += fireColour * firePower * fall * (a * rake * 9.0 + glint * 0.35);

    // A gentle vignette
    c *= 1.0 - 0.35 * dot(q - 0.5, q - 0.5);
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""
