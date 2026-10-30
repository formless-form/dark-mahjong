package com.vortessence.mahjong.ui.components

import android.content.Context
import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.vortessence.mahjong.data.model.TableSurface
import kotlin.math.exp

/**
 * Draws the tabletop: procedural green felt, walnut, or Thai straw mat (redrawn from
 * measurements of a real woven sedge mat), seen through [TableCamera]'s tilted perspective and lit
 * by a single warm lamp above the middle of the table, with the room falling away into darkness
 * around it. Its colour never changes, so it is drawn once and kept; only its light is worked out
 * each frame.
 */
object TableSurfaceRenderer {

    // Unzoomed screen <-> table plane, as in the shaders: shared by all three table passes
    private const val CAMERA_AGSL = """
uniform float2 res;
uniform float tilt;
uniform float camDist;
uniform float zoom;     // the player's pinch zoom
uniform float2 pan;     // screen position = p * zoom + pan
// The tabletop plane point (in table heights, v up) under unzoomed canvas point p, through the
// tilted camera (inverse of TableCamera.planeToScreen); fp is about one screen pixel's size there
float2 tablePoint(float2 p, out float fp) {
    float2 s = float2((p.x - 0.5 * res.x) / res.y, -(p.y - 0.5 * res.y) / res.y);
    float ct = cos(tilt);
    float st = sin(tilt);
    float v = s.y * camDist / (ct * camDist - s.y * st);
    float z = camDist + v * st;
    fp = (z / camDist) / res.y * (1.0 + 0.6 * st) / zoom;
    return float2(s.x * z / camDist, v);
}
"""

    /**
     * The tabletop's own colour, unlit: procedural felt, walnut, straw or slate, in screen
     * pixels (the zoom and pan are worked into it, so fine detail stays sharp when zoomed). It
     * never flickers, so the board draws it into a cached layer, remade only when the zoom, pan,
     * size or surface changes.
     */
    private const val ALBEDO_AGSL = """
uniform float mode;     // 0 green felt mat, 1 walnut, 2 Thai straw mat, 3 slate (TableSurface order)
const float WALNUT_SCALE = 1.2;
const float STRAW_SCALE = 1.1;
$CAMERA_AGSL
$SURFACES_AGSL
half4 main(float2 p) {
    float fp;
    float2 q = tablePoint((p - pan) / zoom, fp);
    float3 albedo;
    if (mode < 0.5) albedo = felt(q, fp);
    // The walnut a little finer than life, narrower boards, so the tiles on it read larger
    else if (mode < 1.5) albedo = walnut(q * WALNUT_SCALE, fp * WALNUT_SCALE);
    // The straw mat a little finer too, for the same reason
    else if (mode < 2.5) albedo = straw(q * STRAW_SCALE, fp * STRAW_SCALE);
    else albedo = slate(q, fp);
    return half4(half3(albedo), 1.0);
}
"""

    /**
     * The light on the table, multiplied over its colour every frame: the lamp's pool, the
     * firelight and its traced shadows, the table's ambient occlusion, the screen's vignette and
     * the room's exposure. Drawn under the zoom (p is the unzoomed canvas point). Where the table
     * is hidden under tiles, nothing is worked out.
     */
    private const val TABLE_LIGHT_AGSL = """
$CAMERA_AGSL
$FLAME_AGSL
half4 main(float2 p) {
    float fp;
    float2 q = tablePoint(p, fp);
    if (underTiles(q)) return half4(0.0, 0.0, 0.0, 1.0);
    float2 lq = (q - LAMP_AT) * float2(1.0, 0.85);
    float pool = 0.12 + 0.95 * exp(-dot(lq, lq) * 2.6);
    float fire = fireShadow(q, 0.0);
    // In the fire's shadow, some of the lamp's light is lost too, so shadows read deeper
    float3 c = (lampLevel * pool * (1.0 - 0.5 * (1.0 - fire)) * LAMP_ON_TABLE + flameLight(q) * fire)
        * ambient(q);
    // Screen-edge vignette deepens the dark corners; the real screen pixel, not the table's
    float2 sp = p * zoom + pan;
    float2 e = (sp / res - 0.5) * float2(1.25, 1.0);
    c *= mix(0.55, 1.0, smoothstep(0.75, 0.2, length(e)));
    // Overall exposure: the room is dark ("Dark Mahjong"); shared with the tiles' lamp light
    c = keepHue(c * 0.9);
    // Dithered, against banding in the dark falloff (larger, as the table's colour scales it down)
    float ign = fract(52.9829189 * fract(dot(floor(sp), float2(0.06711056, 0.00583715))));
    c += (ign - 0.5) * (3.0 / 255.0);
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

    /** Glossy tables' reflections of the lamp and the fire, added over the lit table. */
    private const val TABLE_GLOSS_AGSL = """
uniform float gloss;
uniform float grained;  // 1 for walnut: its grain breaks up the reflection
$CAMERA_AGSL
$FLAME_AGSL
$SURFACES_AGSL
half4 main(float2 p) {
    float fp;
    float2 q = tablePoint(p, fp);
    if (underTiles(q)) return half4(0.0, 0.0, 0.0, 1.0);
    // Glossy surfaces reflect the lamp a little beyond it, stretched by the low viewing angle,
    // and the fire just in front of its flames
    float2 m = (q - LAMP_MIRROR) * float2(3.4, 1.3);
    float2 mc = (q - flame.xy + float2(0.0, 0.05)) * float2(3.4, 2.2);
    float grain = grained > 0.5 ? 0.75 + 0.5 * vnoise(q * 1.2 * float2(60.0, 6.0)) : 1.0;
    float3 c = gloss * grain * (lampLevel * exp(-dot(m, m) * 5.0) * 0.24 * LAMP_ON_TABLE
        + 0.85 * flame.z * exp(-dot(mc, mc) * 4.0) * flameColour * float3(0.26, 0.24, 0.2));
    float2 sp = p * zoom + pan;
    float2 e = (sp / res - 0.5) * float2(1.25, 1.0);
    c *= mix(0.55, 1.0, smoothstep(0.75, 0.2, length(e))) * 0.9;
    // Opaque, as colour is clamped to its alpha: added over the opaque table, it stays opaque
    return half4(half3(c), 1.0);
}
"""

    /** The board's cached table colour: a layer, and the table and view it was drawn for. */
    class AlbedoCache(val layer: GraphicsLayer) {
        internal var shader: RuntimeShader? = null
        internal var surface: TableSurface? = null
        internal var width = 0f
        internal var height = 0f
        internal var size = IntSize.Zero
        internal var zoom = 1f
        internal var pan = Offset.Zero
        // How far the layer reaches beyond the screen on each side, in pixels
        internal var margin = IntOffset.Zero

        /** The layer, drawn for its own view, still fills the screen at [zoom] and [pan]. */
        internal fun covers(zoom: Float, pan: Offset): Boolean {
            val k = zoom / this.zoom
            val left = pan.x + k * (-margin.x - this.pan.x)
            val top = pan.y + k * (-margin.y - this.pan.y)
            val right = pan.x + k * (size.width + margin.x - this.pan.x)
            val bottom = pan.y + k * (size.height + margin.y - this.pan.y)
            return left <= 0.5f && top <= 0.5f && right >= size.width - 0.5f && bottom >= size.height - 0.5f
        }
    }

    private var lightShader: RuntimeShader? = null
    private var glossShader: RuntimeShader? = null
    private var flameShader: RuntimeShader? = null
    private var raisedShader: RuntimeShader? = null
    private var previewAlbedo: RuntimeShader? = null

    /**
     * Draws the whole table at once (previews): its colour, then its light. The board instead
     * draws [drawTableAlbedo] and [drawTableLight] itself, the first cached.
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
        flame: FlameLight = FlameLight.NONE,
        shadowMap: BoardShadowMap.Placed? = null,
        fireShadows: Boolean = false,
    ) {
        val s = previewAlbedo ?: RuntimeShader(ALBEDO_AGSL).also { previewAlbedo = it }
        setAlbedoUniforms(s, surface, width, height, context, zoom, pan)
        scope.drawRect(ShaderBrush(s))
        drawTableLight(scope, surface, width, height, zoom, pan, flame, shadowMap, fireShadows)
    }

    /**
     * The table's colour in screen pixels, from [cache] if nothing it depends on has changed.
     * Drawn before the zoom is applied.
     *
     * While the view is moving (a pinch, a pan, the spring back to the whole board: not
     * [settled]), the colour drawn for the view before is scaled and shifted to follow it, as the
     * zoom is a plain scale and shift of the screen, and only drawn afresh when it would no longer
     * fill the screen. Once the view settles it is drawn again at its sharpest. Some surfaces cost
     * a great deal to draw (the slate most), so this keeps pinches smooth.
     */
    fun drawTableAlbedo(
        scope: DrawScope,
        cache: AlbedoCache,
        surface: TableSurface,
        width: Float,
        height: Float,
        context: Context?,
        zoom: Float,
        pan: Offset,
        settled: Boolean = true,
    ) {
        val size = IntSize(scope.size.width.roundToInt(), scope.size.height.roundToInt())
        val sameTable = cache.surface == surface && cache.width == width && cache.height == height && cache.size == size
        val sameView = cache.zoom == zoom && cache.pan == pan
        if (!sameTable || (!sameView && (settled || !cache.covers(zoom, pan)))) {
            // The whole board can't pan, and zooming in from it stays within the view; zoomed in,
            // the layer reaches past the screen, so a pan or a pinch out can follow it for a while
            val m = if (zoom > 1.001f) {
                IntOffset((size.width * ALBEDO_MARGIN).roundToInt(), (size.height * ALBEDO_MARGIN).roundToInt())
            } else {
                IntOffset.Zero
            }
            val s = cache.shader ?: RuntimeShader(ALBEDO_AGSL).also { cache.shader = it }
            setAlbedoUniforms(s, surface, width, height, context, zoom, pan + Offset(m.x.toFloat(), m.y.toFloat()))
            cache.layer.compositingStrategy = CompositingStrategy.Offscreen
            cache.layer.topLeft = IntOffset(-m.x, -m.y)
            with(scope) {
                cache.layer.record(IntSize(size.width + 2 * m.x, size.height + 2 * m.y)) { drawRect(ShaderBrush(s)) }
            }
            cache.surface = surface
            cache.width = width
            cache.height = height
            cache.size = size
            cache.zoom = zoom
            cache.pan = pan
            cache.margin = m
        }
        if (cache.zoom == zoom && cache.pan == pan) {
            scope.drawLayer(cache.layer)
        } else {
            // Drawn for another view: screen = scene * zoom + pan, for each
            val k = zoom / cache.zoom
            scope.withTransform({
                translate(pan.x - cache.pan.x * k, pan.y - cache.pan.y * k)
                scale(k, k, Offset.Zero)
            }) {
                drawLayer(cache.layer)
            }
        }
    }

    // How far the cached colour reaches past each side of the screen once zoomed in, as a share
    // of the screen
    private const val ALBEDO_MARGIN = 0.2f

    /**
     * The light on the table, multiplied over its colour, and the reflections of a glossy
     * surface added over that. Drawn under the zoom. [cullUnder]: [shadowMap] matches the tiles
     * as drawn, so the table hidden under them can be skipped.
     */
    fun drawTableLight(
        scope: DrawScope,
        surface: TableSurface,
        width: Float,
        height: Float,
        zoom: Float,
        pan: Offset,
        flame: FlameLight,
        shadowMap: BoardShadowMap.Placed?,
        fireShadows: Boolean,
        cullUnder: Boolean = false,
    ) {
        val light = lightShader ?: RuntimeShader(TABLE_LIGHT_AGSL).also { lightShader = it }
        setCameraUniforms(light, width, height, zoom, pan)
        flame.setUniforms(light)
        setShadowUniforms(light, shadowMap, fireShadows, cullUnder)
        scope.drawRect(ShaderBrush(light), blendMode = BlendMode.Modulate)
        val gloss = when (surface) {
            TableSurface.WALNUT -> 1f
            TableSurface.THAI_STRAW -> 0.1f
            // Honed slate: a faint satin sheen
            TableSurface.SLATE -> 0.18f
            TableSurface.GREEN_MAT -> 0f
        }
        if (gloss > 0f) {
            val g = glossShader ?: RuntimeShader(TABLE_GLOSS_AGSL).also { glossShader = it }
            setCameraUniforms(g, width, height, zoom, pan)
            flame.setUniforms(g)
            setShadowUniforms(g, shadowMap, fireShadows, cullUnder)
            g.setFloatUniform("gloss", gloss)
            g.setFloatUniform("grained", if (surface == TableSurface.WALNUT) 1f else 0f)
            scope.drawRect(ShaderBrush(g), blendMode = BlendMode.Plus)
        }
    }

    /**
     * The lamp and firelight for the tiles in flat table pixels: drawn with BlendMode.Modulate
     * over everything on the table, matching the table's own light. [fresh]: [shadowMap] matches
     * the tiles as drawn, so the fire's shadow is traced only where a tile stands.
     */
    fun tileLight(
        width: Float,
        height: Float,
        flame: FlameLight,
        shadowMap: BoardShadowMap.Placed? = null,
        fireShadows: Boolean = false,
        fresh: Boolean = false,
    ): ShaderBrush {
        val s = flameShader ?: RuntimeShader(FLAME_TILE_AGSL).also { flameShader = it }
        s.setFloatUniform("res", width, height)
        flame.setUniforms(s)
        setShadowUniforms(s, shadowMap, fireShadows, fresh)
        return ShaderBrush(s)
    }

    /**
     * [tileLight] without the board's shadows, for tiles lifted clear of it (a matched pair in
     * flight): traced from the board's height map, those would print the outlines of the tiles
     * beneath onto them, so they would look see-through. Its own shader, as [tileLight]'s is
     * drawn in the same frame with other uniforms.
     */
    fun raisedTileLight(width: Float, height: Float, flame: FlameLight): ShaderBrush {
        val s = raisedShader ?: RuntimeShader(FLAME_TILE_AGSL).also { raisedShader = it }
        s.setFloatUniform("res", width, height)
        flame.setUniforms(s)
        setShadowUniforms(s, null, fireShadows = false, cull = false)
        return ShaderBrush(s)
    }

    private fun setShadowUniforms(s: RuntimeShader, shadowMap: BoardShadowMap.Placed?, fireShadows: Boolean, cull: Boolean) {
        if (shadowMap != null) shadowMap.setUniforms(s, fireShadows) else BoardShadowMap.setNone(s)
        s.setFloatUniform("cullUnder", if (cull && shadowMap != null) 1f else 0f)
    }

    private fun setCameraUniforms(s: RuntimeShader, width: Float, height: Float, zoom: Float, pan: Offset) {
        s.setFloatUniform("res", width, height)
        s.setFloatUniform("tilt", Math.toRadians(TableCamera.TILT_DEGREES.toDouble()).toFloat())
        s.setFloatUniform("camDist", TableCamera.DISTANCE)
        s.setFloatUniform("zoom", zoom)
        s.setFloatUniform("pan", pan.x, pan.y)
    }

    private fun setAlbedoUniforms(s: RuntimeShader, surface: TableSurface, width: Float, height: Float, context: Context?, zoom: Float, pan: Offset) {
        setCameraUniforms(s, width, height, zoom, pan)
        s.setFloatUniform("mode", surface.ordinal.toFloat())
    }

    /** Strength of the lamp's and the flame's glossy reflection at plane point (u, v), for tile glare. */
    fun flameGlare(u: Float, v: Float, flame: FlameLight): Float {
        val mu = (u - flame.u) * 3.4f
        val mv = (v - flame.v + 0.05f) * 2.2f
        // The dimmed lamp's own reflection, plus a fainter one of the flame
        return flame.lamp * lampGlare(u, v) + 0.7f * flame.power * exp(-(mu * mu + mv * mv) * 4f)
    }

    /** Lamp brightness at plane point (u, v), matching the shader: used to light the tiles. */
    fun lampLight(u: Float, v: Float): Float {
        val du = u - LAMP_U
        val dv = (v - LAMP_V) * 0.85f
        return 0.12f + 0.95f * kotlin.math.exp(-(du * du + dv * dv) * LAMP_FALLOFF)
    }

    /** Strength of the lamp's glossy reflection at plane point (u, v), for tile glare. */
    fun lampGlare(u: Float, v: Float): Float {
        val mu = (u - LAMP_MIRROR_U) * 3.0f
        val mv = (v - LAMP_MIRROR_V) * 1.6f
        return kotlin.math.exp(-(mu * mu + mv * mv) * 3.5f)
    }

    private const val LAMP_FALLOFF = 2.6f
}

/**
 * The lamp's place over the table, in table heights (u right, v up the table): to the right of
 * the middle and up the table, so it lights the board at a slant rather than from straight above.
 * Its height (in table heights) sets how far its shadows lean.
 */
internal const val LAMP_U = 0.14f
internal const val LAMP_V = 0.16f
internal const val LAMP_HEIGHT = 0.9f

// A glossy surface reflects the lamp a little beyond it and nearer the middle, as seen from the
// tilted camera
private const val LAMP_MIRROR_U = LAMP_U * 0.6f
private const val LAMP_MIRROR_V = LAMP_V + 0.2f

/**
 * Firelight at plane point q (in table heights, v up), shared by both shaders: one warm source
 * out of view, a soft inverse-square pool. It is added to the lamp turned down rather than
 * replacing it: the lamp overhead keeps every tile face readable, wherever the fire's light
 * falls short. All of it comes from [FlameLight], fresh each frame.
 *
 * With long shadows on, the fire's light is traced per pixel from each point towards the flames
 * through the board's height map ([BoardShadowMap]). The fire burns low beside the table, so its
 * light skims the board and a stack throws a shadow several tiles long. The table's ambient
 * occlusion is a cheap lookup in the same map. Both shaders declare res (the flat table's size in
 * pixels) before this.
 */
private const val FLAME_AGSL = """
// The lamp (TableSurfaceRenderer's LAMP_ constants): over the table a little to the right of
// the board and up the table from it, so it lights the board at a slant; warm, about 2700 K
const float2 LAMP_AT = float2($LAMP_U, $LAMP_V);
// Where a glossy surface shows its reflection, as the tilted camera sees it
const float2 LAMP_MIRROR = float2($LAMP_MIRROR_U, $LAMP_MIRROR_V);
const float3 LAMP_ON_TABLE = float3(1.0, 0.86, 0.70);
const float3 LAMP_ON_TILES = float3(1.0, 0.89, 0.76);
uniform float4 flame;       // u, v, power, reach (squared distance at which it halves)
uniform float3 flameColour;
uniform float lampLevel;    // the lamp's strength alongside the flame
uniform shader heightMap;   // the board's height map, at the flat table's resolution (BoardShadowMap)
uniform shader aoMap;       // the table's ambient occlusion in green, at a coarser resolution
uniform float4 mapXform;    // table pixels now to map pixels: scale x, y, then offset x, y (the board may have moved)
uniform float4 shade;       // fire shadows on (1) or off, occlusion map pixels per map pixel, one layer's height, the tallest stack's
uniform float aoStrength;   // how deep the table's ambient occlusion goes (0 with no map)
uniform float flameHeight;  // how high the flames stand above the table this frame, in table heights
const int SHADOW_STEPS = 40;
// Brings an over-bright colour back within range whole, keeping its hue. Clamping each channel
// on its own would clip red first, so a bright orange flare would drift towards yellow-green.
float3 keepHue(float3 c) {
    return c / max(1.0, max(c.r, max(c.g, c.b)));
}
float3 flameLight(float2 q) {
    float2 d = q - flame.xy;
    return flame.z / (1.0 + dot(d, d) / flame.w) * flameColour;
}
// The height map's pixel under q (in table heights), wherever the board has moved since it was made
float2 tablePixel(float2 q) {
    return float2(q.x * res.y + 0.5 * res.x, 0.5 * res.y - q.y * res.y) * mapXform.xy + mapXform.zw;
}
// How tall the board stands at q (0 on the bare table), in table heights
float boardHeight(float2 q) {
    return heightMap.eval(tablePixel(q)).a * (255.0 / 31.0) * shade.z;
}
// The height of the surface at q itself: its own pixel, unblended, so a lower tile's edge next to
// a taller one isn't lifted into the light by its neighbour's height
float surfaceHeight(float2 q) {
    return heightMap.eval(floor(tablePixel(q)) + 0.5).a * (255.0 / 31.0) * shade.z;
}
// The table's ambient occlusion: lower where the tiles hem it in
float ambient(float2 q) {
    return 1.0 - aoStrength * aoMap.eval(tablePixel(q) * shade.y).g;
}
uniform float cullUnder;    // 1 when the height map matches the board as drawn: the table hidden under tiles is skipped
uniform float4 boardBox;    // the standing tiles' bounds in map pixels (left, top, right, bottom), with a margin; left > right when none stand
// Wholly under a tile, a couple of map pixels in from every edge: the tiles drawn over it hide it
bool underTiles(float2 q) {
    if (cullUnder < 0.5) return false;
    float2 t = tablePixel(q);
    if (t.x < boardBox.x || t.x > boardBox.z || t.y < boardBox.y || t.y > boardBox.w) return false;
    float lowest = min(min(heightMap.eval(t + float2(-2.0, -2.0)).a, heightMap.eval(t + float2(2.0, -2.0)).a),
                       min(heightMap.eval(t + float2(-2.0, 2.0)).a, heightMap.eval(t + float2(2.0, 2.0)).a));
    return lowest >= 30.5 / 255.0;
}
// The share of the line from q to q + d (0 to 1 along it) that crosses the board's bounds: the
// first and last, the first beyond the last if it misses them
float2 overBoard(float2 q, float2 d) {
    if (boardBox.x > boardBox.z) return float2(1.0, 0.0);
    float2 a = tablePixel(q);
    float2 e = tablePixel(q + d) - a;
    // Never quite zero, so a line along an edge still divides
    e = float2(e.x < 0.0 ? min(e.x, -1e-3) : max(e.x, 1e-3), e.y < 0.0 ? min(e.y, -1e-3) : max(e.y, 1e-3));
    float2 t0 = (boardBox.xy - a) / e;
    float2 t1 = (boardBox.zw - a) / e;
    float2 lo = min(t0, t1);
    float2 hi = max(t0, t1);
    return float2(max(max(lo.x, lo.y), 0.0), min(min(hi.x, hi.y), 1.0));
}
// How much of the fire reaches q, standing h0 above the table: 1 in its light, 0 in full shadow.
// Marches from q towards the flames as far as the ray stays below the tallest stack, and takes
// the deepest occlusion along the way, softened as the ray reaches away (the fire is broad).
// Only the steps over the board are taken (off it the table is bare, so they would find
// nothing), and none after one finds full shadow: the result is the same as taking them all.
float fireShadow(float2 q, float h0) {
    if (shade.x < 0.5 || flame.z <= 0.0 || h0 >= shade.w) return 1.0;
    float2 toFire = flame.xy - q;
    float dist = length(toFire);
    float rise = (flameHeight - h0) / dist;
    float reach = min(dist, (shade.w - h0) / max(rise, 1e-4));
    float2 dir = toFire / dist;
    float start = 0.6 * shade.z;
    float2 span = overBoard(q + dir * start, dir * reach);
    if (span.x > span.y) return 1.0;
    // A different start for each pixel, so the steps don't show as bands
    float jitter = fract(52.9829189 * fract(dot(q * res.y, float2(0.06711056, 0.00583715))));
    float stride = reach / float(SHADOW_STEPS);
    // Step i lies (i + jitter) / SHADOW_STEPS of the way along: the first and last over the board,
    // a step to spare each side
    float first = max(ceil(span.x * float(SHADOW_STEPS) - jitter) - 1.0, 0.0);
    float last = min(span.y * float(SHADOW_STEPS) - jitter + 1.0, float(SHADOW_STEPS) - 1.0);
    float occluded = 0.0;
    for (int k = 0; k < SHADOW_STEPS; k++) {
        float i = first + float(k);
        if (i > last || occluded >= 1.0) break;
        float s = stride * (i + jitter) + start;
        // Less a little, so a tile's own softened edge never shades it
        float above = boardHeight(q + dir * s) - (h0 + s * rise) - 0.35 * shade.z;
        float soft = 0.8 * shade.z + 0.12 * s;
        occluded = max(occluded, clamp(above / soft, 0.0, 1.0));
    }
    return 1.0 - occluded;
}
"""

private const val FLAME_TILE_AGSL = """
uniform float2 res;
$FLAME_AGSL
half4 main(float2 p) {
    float2 q = float2((p.x - 0.5 * res.x) / res.y, (0.5 * res.y - p.y) / res.y);
    // The lamp's light on the tiles (softer than on the table, so no face is ever too dark to
    // read), plus the flame; times the room's exposure
    float2 l = (q - LAMP_AT) / 0.95;
    float lamp = 0.30 + 0.70 * exp(-dot(l, l) * 3.2);
    // Traced only where a tile stands (when the height map is up to date): elsewhere on the
    // layer there are only the tiles' shadows, black, which no light changes
    float h0 = surfaceHeight(q);
    float fire = (cullUnder > 0.5 && h0 <= 0.0) ? 1.0 : fireShadow(q, h0);
    // In the fire's shadow, some of the lamp's light is lost too, so shadows read deeper
    float3 c = (lampLevel * lamp * (1.0 - 0.5 * (1.0 - fire)) * LAMP_ON_TILES + flameLight(q) * fire)
        * 0.81;
    return half4(half3(keepHue(c)), 1.0);
}
"""


private const val SURFACES_AGSL = """
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

// ---- Slate: the menu screens' stone (SlateBackdrop) as a tabletop. Thin layers split along
// their cleavage into terraces, with hairline cleavage lines, flakes, grit and pits; its relief is
// shaded here by the lamp hanging above and beyond the middle of the table, so the cached colour
// carries it. Detail finer than a pixel fades out, so it never shimmers ----
float slateFbm3(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v / 0.875;
}

float slateHeight(float2 u, float fine) {
    float2 s = float2(u.x * 0.55 + u.y * 0.12, u.y * 2.2);
    float layers = fbm(s * 0.6);
    float steps = floor(layers * 7.0) / 7.0 + smoothstep(0.0, 0.12, fract(layers * 7.0)) / 7.0;
    float2 c = float2(u.x * 3.0 + u.y * 0.6, u.y * 26.0);
    float hair = 1.0 - abs(vnoise(c + slateFbm3(u * 2.0) * 3.0) * 2.0 - 1.0);
    float flake = smoothstep(0.62, 0.7, vnoise(u * float2(9.0, 22.0) + 3.0)) * 0.6;
    float grit = vnoise(u * 55.0) * 0.6 + vnoise(u * 120.0) * 0.4;
    float pit = smoothstep(0.93, 0.98, vnoise(u * 38.0 + 7.0));
    return steps * 1.3 + fbm(u * 3.0) * 0.22 + hair * 0.05 + flake * 0.03
        + (grit * 0.03 - pit * 0.05) * fine;
}

float3 slate(float2 q, float fp) {
    // About as large against the tiles as the menu slate is against the screen
    float k = 7.0;
    float2 u = q * k;
    float fine = 1.0 - smoothstep(0.0006, 0.0016, fp);
    float e = max(fp, 0.0003) * k;
    float h = slateHeight(u, fine);
    float3 n = normalize(float3(h - slateHeight(u + float2(e, 0.0), fine), h - slateHeight(u + float2(0.0, e), fine), e * 3.5));
    // The lamp hangs above, a little beyond the middle (v up is away from the player)
    float3 l = normalize(float3(-0.15, 0.45, 0.88));
    float shade = 0.62 + 0.5 * max(dot(n, l), 0.0);
    float tone = fbm(u * 0.35 + 4.0);
    float3 col = mix(float3(0.05, 0.059, 0.063), float3(0.13, 0.143, 0.15), tone);
    col = mix(col, float3(0.12, 0.09, 0.07), smoothstep(0.62, 0.8, fbm(u * float2(0.4, 1.6) + 9.0)) * 0.3);
    col *= 0.85 + (0.25 * vnoise(u * 70.0) - 0.125) * fine + 0.1 * fbm(u * 18.0);
    col += step(0.992, hash21(floor(u * 160.0))) * 0.05 * fine;
    // Lighter than the menu slate: the table's light multiplies it, falling away into the dark
    return col * shade * 2.1;
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
"""
