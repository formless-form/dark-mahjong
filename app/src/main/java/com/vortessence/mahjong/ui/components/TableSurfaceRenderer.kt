package com.vortessence.mahjong.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import kotlin.math.max
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Draws the tabletop (felt, Thai straw mat, slate, elm wood or silk brocade) through
 * [TableCamera]'s tilted perspective, lit by one warm lamp in a dark room. Its colour is drawn once
 * and cached; only its light is worked out each frame.
 */
object TableSurfaceRenderer {

    // Unzoomed screen <-> table plane, shared by the table shaders
    private const val CAMERA_AGSL = """
uniform float2 res;
uniform float tilt;
uniform float camDist;
uniform float zoom;     // pinch zoom
uniform float2 pan;     // screen position = p * zoom + pan
// Table-plane point (table heights, v up) under unzoomed canvas point p (inverse of
// TableCamera.planeToScreen); fp is about one screen pixel's size there
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
     * The tabletop's unlit colour in screen pixels (zoom and pan applied, so detail stays sharp
     * zoomed). Cached in a layer, remade only when zoom, pan, size or surface change.
     */
    private const val ALBEDO_AGSL = """
uniform float mode;     // TableSurface order: 0 felt, 2 straw, 3 slate, 4 brocade (1, elm, has ELM_ALBEDO_AGSL)
const float STRAW_SCALE = 1.1;
$CAMERA_AGSL
$SURFACES_AGSL
$STONE_AGSL
$SLATE_TABLE_AGSL
half4 main(float2 p) {
    float fp;
    float2 q = tablePoint((p - pan) / zoom, fp);
    float3 albedo;
    if (mode < 0.5) albedo = felt(q, fp);
    else if (mode < 2.5) albedo = straw(q * STRAW_SCALE, fp * STRAW_SCALE);
    // Colour only: the relief is lit separately (SLATE_SURFACE_AGSL)
    else if (mode < 3.5) albedo = stoneColour(q * SLATE_SCALE) * SLATE_GAIN;
    else albedo = silkBrocade(q, fp);
    return half4(half3(albedo), 1.0);
}
"""

    /**
     * The elm wood's colour, as [ALBEDO_AGSL]. Its own shader: it is the costliest table, and where
     * all branches are evaluated (JVM renders, some phones) it would slow every table.
     */
    private const val ELM_ALBEDO_AGSL = """
$CAMERA_AGSL
$SURFACES_AGSL
$ELM_WOOD_AGSL
half4 main(float2 p) {
    float fp;
    float2 q = tablePoint((p - pan) / zoom, fp);
    return half4(half3(elmWood(q * elmMm, fp * elmMm)), 1.0);
}
"""

    /**
     * The slate's normal per screen pixel (0.5 + 0.5 n; x right, y up the table, z up), made once
     * per view like its colour, so the lamp and fire can light its ridges each frame.
     */
    private const val SLATE_SURFACE_AGSL = """
$CAMERA_AGSL
$STONE_AGSL
$SLATE_TABLE_AGSL
half4 main(float2 p) {
    float fp;
    float2 q = tablePoint((p - pan) / zoom, fp);
    // Slope over a pixel: sub-pixel detail is left out (it would make the light sparkle)
    float e = max(fp, 1e-5) * SLATE_SCALE;
    float3 n = stoneNormal(q * SLATE_SCALE, e, e);
    return half4(half3(n * 0.5 + 0.5), 1.0);
}
"""

    /** The elm wood's normals, as the slate's. */
    private const val ELM_SURFACE_AGSL = """
$CAMERA_AGSL
$SURFACES_AGSL
$ELM_WOOD_AGSL
half4 main(float2 p) {
    float fp;
    float2 q = tablePoint((p - pan) / zoom, fp);
    float3 n = elmWoodNormal(q * elmMm, fp * elmMm);
    return half4(half3(n * 0.5 + 0.5), 1.0);
}
"""

    /** The surface map's normal under an unzoomed canvas point; level where there is no map. */
    private const val RELIEF_AGSL = """
uniform shader surface;      // surface map (SLATE_/ELM_SURFACE_AGSL) for the view it was made for
uniform float4 surfaceXform; // unzoomed canvas point to map pixel: scale x, y, then offset x, y
uniform float2 surfaceSize;  // map size, pixels
uniform float relief;        // 1 when there is a map
uniform float3 reliefLight;  // lamp height (table heights); share of lamp, then fire, light falling whichever way it faces
float3 surfaceNormal(float2 p) {
    if (relief < 0.5) return float3(0.0, 0.0, 1.0);
    // Past the map's edge (a pinch or pan since it was made), fade to level over a few pixels
    // rather than smear the edge; the map is remade once the view settles
    float2 at = p * surfaceXform.xy + surfaceXform.zw;
    float2 inside = min(at, surfaceSize - at);
    float cover = clamp(min(inside.x, inside.y) / 8.0, 0.0, 1.0);
    return normalize(mix(float3(0.0, 0.0, 1.0), surface.eval(at).rgb * 2.0 - 1.0, cover));
}
"""

    /**
     * The table's light, multiplied over its colour each frame: lamp pool, firelight and traced
     * shadows, ambient occlusion, vignette and exposure. Drawn under the zoom (p is the unzoomed
     * canvas point); skipped under tiles.
     */
    private const val TABLE_LIGHT_AGSL = """
$CAMERA_AGSL
$FLAME_AGSL
$RELIEF_AGSL
// Brightness of a surface facing n against a level one, in light from l; [even] is the share
// falling whichever way it faces
float facing(float3 n, float3 l, float even) {
    return (even + max(dot(n, l), 0.0)) / (even + max(l.z, 1e-3));
}
half4 main(float2 p) {
    float fp;
    float2 q = tablePoint(p, fp);
    if (underTiles(q)) return half4(0.0, 0.0, 0.0, 1.0);
    float2 lq = fromLamp(q, LAMP_AT) * float2(1.0, 0.85);
    float pool = 0.12 + 0.95 * exp(-dot(lq, lq) * 2.6);
    float fire = fireShadow(q, 0.0);
    // Relief (slate, elm) raked by a low lamp and by the low fire, so ridges facing it flicker
    float lampFacing = 1.0;
    float fireFacing = 1.0;
    if (relief > 0.5) {
        float3 n = surfaceNormal(p);
        lampFacing = facing(n, normalize(float3(lampAt(LAMP_AT) - q, reliefLight.x)), reliefLight.y);
        fireFacing = min(facing(n, normalize(float3(flame.xy - q, flameHeight)), reliefLight.z), 3.0);
    }
    // Fire shadow dims the lamp too, so shadows read deeper
    float3 c = (lampLevel * pool * (1.0 - 0.5 * (1.0 - fire)) * LAMP_ON_TABLE * lampFacing
        + flameLight(q) * fire * fireFacing) * ambient(q);
    // Vignette, by real screen pixel
    float2 sp = p * zoom + pan;
    float2 e = (sp / res - 0.5) * float2(1.25, 1.0);
    c *= mix(0.55, 1.0, smoothstep(0.75, 0.2, length(e)));
    // Exposure (the room is dark); shared with the tiles' lamp light
    c = keepHue(c * 0.9);
    // Dither against banding (larger, as the table's colour scales it down)
    float ign = fract(52.9829189 * fract(dot(floor(sp), float2(0.06711056, 0.00583715))));
    c += (ign - 0.5) * (3.0 / 255.0);
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

    /** Glossy tables' reflections of the lamp and the fire, added over the lit table. */
    private const val TABLE_GLOSS_AGSL = """
uniform float gloss;
uniform float2 polish;  // polished relief (elm): how far slopes throw reflections aside; crest shine
$CAMERA_AGSL
$FLAME_AGSL
$SURFACES_AGSL
$RELIEF_AGSL
// Towards the eye, at the camera's tilt (TableCamera)
const float3 TO_EYE = float3(0.0, -0.342, 0.940);
half4 main(float2 p) {
    float fp;
    float2 q = tablePoint(p, fp);
    if (underTiles(q)) return half4(0.0, 0.0, 0.0, 1.0);
    float3 n = surfaceNormal(p);
    // The lamp reflects a little beyond it, stretched by the low view, the fire just in front of
    // its flames; a polished relief's slopes shift it aside, breaking it up along the ridges
    float2 thrown = q + n.xy * polish.x;
    float2 m = fromLamp(thrown, LAMP_MIRROR) * float2(3.4, 1.3);
    float2 mc = (thrown - flame.xy + float2(0.0, 0.05)) * float2(3.4 / flameSpread, 2.2);
    float3 c = gloss * (lampLevel * exp(-dot(m, m) * 5.0) * 0.24 * LAMP_ON_TABLE
        + 0.85 * flame.z * exp(-dot(mc, mc) * 4.0) * flameColour * float3(0.26, 0.24, 0.2));
    // ...and crests facing halfway between lamp and eye shine across the lamp's pool
    if (polish.y > 0.0 && relief > 0.5) {
        float2 lq = fromLamp(q, LAMP_AT) * float2(1.0, 0.85);
        float pool = 0.12 + 0.95 * exp(-dot(lq, lq) * 2.6);
        float3 h = normalize(normalize(float3(lampAt(LAMP_AT) - q, reliefLight.x)) + TO_EYE);
        c += polish.y * lampLevel * pool * pow(max(dot(n, h), 0.0), 64.0) * LAMP_ON_TABLE;
    }
    float2 sp = p * zoom + pan;
    float2 e = (sp / res - 0.5) * float2(1.25, 1.0);
    c *= mix(0.55, 1.0, smoothstep(0.75, 0.2, length(e))) * 0.9;
    // Alpha 1, as colour is clamped to alpha; added over the opaque table, it stays opaque
    return half4(half3(c), 1.0);
}
"""

    /** The board's cached table colour: a layer, and the table and view it was drawn for. */
    class AlbedoCache(val layer: GraphicsLayer) {
        internal var shader: RuntimeShader? = null
        // The AGSL [shader] was made from (elm has its own)
        internal var shaderOf: String? = null
        internal var surface: TableSurface? = null
        internal var width = 0f
        internal var height = 0f
        internal var size = IntSize.Zero
        internal var zoom = 1f
        internal var pan = Offset.Zero
        // Tile width on the table: sets the elm's scale
        internal var tile = 0f
        // The layer's reach past each screen edge, pixels
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

    /** A table's surface map (SLATE_/ELM_SURFACE_AGSL) and the view it was made for. */
    class Relief internal constructor(
        val surface: TableSurface,
        internal val shader: BitmapShader,
        internal val xform: FloatArray,
        internal val width: Float,
        internal val height: Float,
    )

    /** [surface] has a relief, lit through a surface map ([bakeRelief]). */
    fun hasRelief(surface: TableSurface) = surface == TableSurface.SLATE || surface == TableSurface.ELM_WOOD

    /** A real tile's width, mm: the elm wood is drawn life-size against it. */
    const val TILE_MM = 24f

    // Millimetres per table height on a usual board (a tile about 0.04 of it), when unknown
    private const val USUAL_TABLE_MM = 600f

    // Millimetres per table height, from tile width and table height in pixels
    private fun tableMm(tile: Float, height: Float) = if (tile > 0f) TILE_MM * height / tile else USUAL_TABLE_MM

    /** For JVM renders (previews), which can't read a layer back: draw the surface map on the CPU. */
    var softwareRelief = false

    private fun setElmUniforms(s: RuntimeShader, mm: Float) {
        s.setFloatUniform("elmMm", mm)
    }

    // Relief lighting: lamp height (table heights), then the share of lamp and of fire light falling
    // whichever way the surface faces
    private fun reliefLight(surface: TableSurface): FloatArray = when (surface) {
        TableSurface.ELM_WOOD -> floatArrayOf(ELM_LAMP_HEIGHT, ELM_LAMP_EVEN, ELM_FIRE_EVEN)
        else -> floatArrayOf(RELIEF_LAMP_HEIGHT, SLATE_LAMP_EVEN, SLATE_FIRE_EVEN)
    }

    private fun setReliefUniforms(s: RuntimeShader, surface: TableSurface, relief: Relief?) {
        if (relief != null && relief.surface == surface) {
            s.setInputShader("surface", relief.shader)
            s.setFloatUniform("surfaceXform", relief.xform)
            s.setFloatUniform("surfaceSize", relief.width, relief.height)
            s.setFloatUniform("relief", 1f)
        } else {
            s.setInputShader("surface", flatSurface)
            s.setFloatUniform("surfaceXform", 1f, 1f, 0f, 0f)
            s.setFloatUniform("surfaceSize", 1f, 1f)
            s.setFloatUniform("relief", 0f)
        }
        s.setFloatUniform("reliefLight", reliefLight(surface))
    }

    private var lightShader: RuntimeShader? = null
    private val surfaceShaders = HashMap<TableSurface, RuntimeShader>()
    // A level normal, for no relief
    private val flatSurface by lazy {
        BitmapShader(Bitmap.createBitmap(intArrayOf(0xFF8080FF.toInt()), 1, 1, Bitmap.Config.ARGB_8888), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private var glossShader: RuntimeShader? = null
    /**
     * Whether firelight follows a stone set's relief (its surface map). Off, the stone keeps its
     * pictures' baked light by the fire too, as always by the lamp (Gareth's pick, 2026-11-14:
     * "33 and 35"). For previews to compare: the board makes a surface map only by the fire, never
     * in low-performance mode.
     */
    var tileRelief by mutableStateOf(true)

    private var flameShader: RuntimeShader? = null
    private var raisedShader: RuntimeShader? = null
    private val previewAlbedos = HashMap<String, RuntimeShader>()

    private fun albedoAgsl(surface: TableSurface) = if (surface == TableSurface.ELM_WOOD) ELM_ALBEDO_AGSL else ALBEDO_AGSL

    /**
     * The whole table, colour then light (previews). The board calls [drawTableAlbedo] (cached)
     * and [drawTableLight] itself.
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
        // Tile width in table pixels, if there is a board: sets the elm's scale
        tile: Float = 0f,
    ) {
        val s = albedoAgsl(surface).let { agsl -> previewAlbedos.getOrPut(agsl) { RuntimeShader(agsl) } }
        setAlbedoUniforms(s, surface, width, height, context, zoom, pan, tile)
        scope.drawRect(ShaderBrush(s))
        drawTableLight(scope, surface, width, height, zoom, pan, flame, shadowMap, fireShadows)
    }

    /**
     * The table's colour in screen pixels, from [cache] when unchanged. Drawn before the zoom.
     *
     * While the view moves (not [settled]), the cached colour is scaled and shifted to follow it
     * (the zoom is a plain scale and shift), redrawn only when it no longer fills the screen, and
     * redrawn sharp once settled. Some surfaces (slate most) are costly: this keeps pinches smooth.
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
        // Tile width in table pixels on the whole board: sets the elm's scale
        tile: Float = 0f,
    ) {
        val size = IntSize(scope.size.width.roundToInt(), scope.size.height.roundToInt())
        val sameTable = cache.surface == surface && cache.width == width && cache.height == height && cache.size == size &&
            cache.tile == tile
        val sameView = cache.zoom == zoom && cache.pan == pan
        if (!sameTable || (!sameView && (settled || !cache.covers(zoom, pan)))) {
            val m = viewMargin(size, zoom)
            val agsl = albedoAgsl(surface)
            val s = cache.shader?.takeIf { cache.shaderOf === agsl } ?: RuntimeShader(agsl).also {
                cache.shader = it
                cache.shaderOf = agsl
            }
            setAlbedoUniforms(s, surface, width, height, context, zoom, pan + Offset(m.x.toFloat(), m.y.toFloat()), tile)
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
            cache.tile = tile
            cache.margin = m
        }
        if (cache.zoom == zoom && cache.pan == pan) {
            scope.drawLayer(cache.layer)
        } else {
            // Cached for another view; screen = scene * zoom + pan for each
            val k = zoom / cache.zoom
            scope.withTransform({
                translate(pan.x - cache.pan.x * k, pan.y - cache.pan.y * k)
                scale(k, k, Offset.Zero)
            }) {
                drawLayer(cache.layer)
            }
        }
    }

    // The cached colour's reach past each screen edge when zoomed in, as a share of the screen
    private const val ALBEDO_MARGIN = 0.2f

    // None for the whole board (it can't pan; zooming in stays inside). Zoomed in, the margin lets
    // a pan or pinch out follow the cached view for a while
    private fun viewMargin(size: IntSize, zoom: Float): IntOffset = if (zoom > 1.001f) {
        IntOffset((size.width * ALBEDO_MARGIN).roundToInt(), (size.height * ALBEDO_MARGIN).roundToInt())
    } else {
        IntOffset.Zero
    }

    /**
     * [surface]'s surface map (slate, elm), made on the GPU for the view at [zoom] and [pan] over
     * [size] plus the cache margin. Null where nothing can be read back (JVM renders, unless
     * [softwareRelief]): the surface stays level. [tile]: tile width in table pixels, whole board.
     */
    suspend fun bakeRelief(
        surface: TableSurface,
        layer: GraphicsLayer,
        density: Density,
        layoutDirection: LayoutDirection,
        size: IntSize,
        width: Float,
        height: Float,
        zoom: Float,
        pan: Offset,
        tile: Float = 0f,
    ): Relief? = runCatching {
        val m = viewMargin(size, zoom)
        val s = surfaceShaders.getOrPut(surface) {
            RuntimeShader(if (surface == TableSurface.ELM_WOOD) ELM_SURFACE_AGSL else SLATE_SURFACE_AGSL)
        }
        setCameraUniforms(s, width, height, zoom, pan + Offset(m.x.toFloat(), m.y.toFloat()))
        if (surface == TableSurface.ELM_WOOD) setElmUniforms(s, tableMm(tile, height))
        val mapSize = IntSize(size.width + 2 * m.x, size.height + 2 * m.y)
        val bitmap = if (softwareRelief) {
            Bitmap.createBitmap(mapSize.width, mapSize.height, Bitmap.Config.ARGB_8888).also {
                android.graphics.Canvas(it).drawRect(
                    0f, 0f, mapSize.width.toFloat(), mapSize.height.toFloat(), android.graphics.Paint().apply { shader = s },
                )
            }
        } else {
            layer.record(density, layoutDirection, mapSize) { drawRect(ShaderBrush(s)) }
            layer.toImageBitmap().asAndroidBitmap()
        }
        // Map pixel for unzoomed canvas point p: p * zoom + pan + margin
        Relief(
            surface,
            BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR },
            floatArrayOf(zoom, zoom, pan.x + m.x, pan.y + m.y),
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
        )
    }.getOrNull()

    /**
     * The table's light multiplied over its colour, then a glossy surface's reflections added.
     * Drawn under the zoom. [cullUnder]: [shadowMap] matches the tiles as drawn, so the table
     * under them is skipped.
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
        // The surface map, once made, for a surface with relief
        relief: Relief? = null,
    ) {
        val light = lightShader ?: RuntimeShader(TABLE_LIGHT_AGSL).also { lightShader = it }
        setCameraUniforms(light, width, height, zoom, pan)
        flame.setUniforms(light)
        setShadowUniforms(light, shadowMap, fireShadows, cullUnder)
        setReliefUniforms(light, surface, relief)
        scope.drawRect(ShaderBrush(light), blendMode = BlendMode.Modulate)
        val gloss = when (surface) {
            // Oiled elm: a soft sheen, over its relief
            TableSurface.ELM_WOOD -> 0.5f
            TableSurface.THAI_STRAW -> 0.1f
            // Honed slate: a faint satin sheen
            TableSurface.SLATE -> 0.18f
            TableSurface.GREEN_MAT -> 0f
            // Silk: a soft sheen
            TableSurface.BROCADE -> 0.3f
        }
        if (gloss > 0f && !PerfSwitches.isOff("gloss")) {
            val g = glossShader ?: RuntimeShader(TABLE_GLOSS_AGSL).also { glossShader = it }
            setCameraUniforms(g, width, height, zoom, pan)
            flame.setUniforms(g)
            setShadowUniforms(g, shadowMap, fireShadows, cullUnder)
            g.setFloatUniform("gloss", gloss)
            // Only the elm's oiled finish follows its relief: the slate's faint sheen lies level
            if (surface == TableSurface.ELM_WOOD) {
                setReliefUniforms(g, surface, relief)
                g.setFloatUniform("polish", ELM_THROW, ELM_SHEEN)
            } else {
                setReliefUniforms(g, surface, null)
                g.setFloatUniform("polish", 0f, 0f)
            }
            scope.drawRect(ShaderBrush(g), blendMode = BlendMode.Plus)
        }
    }

    /**
     * Lamp and firelight for the tiles in flat table pixels, drawn with BlendMode.Modulate to
     * match the table's light. [fresh]: [shadowMap] matches the tiles as drawn, so the fire's
     * shadow is traced only where a tile stands.
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
        s.setFloatUniform("neutralLight", TileKits.current.neutralLight)
        flame.setUniforms(s)
        setShadowUniforms(s, shadowMap, fireShadows, fresh)
        return ShaderBrush(s)
    }

    /**
     * [tileLight] without the board's shadows, for lifted tiles (a matched pair in flight), which
     * would otherwise show the outlines of tiles beneath and look see-through. Its own shader, as
     * [tileLight]'s is used in the same frame with other uniforms.
     */
    fun raisedTileLight(width: Float, height: Float, flame: FlameLight): ShaderBrush {
        val s = raisedShader ?: RuntimeShader(FLAME_TILE_AGSL).also { raisedShader = it }
        s.setFloatUniform("res", width, height)
        s.setFloatUniform("neutralLight", TileKits.current.neutralLight)
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

    private fun setAlbedoUniforms(s: RuntimeShader, surface: TableSurface, width: Float, height: Float, context: Context?, zoom: Float, pan: Offset, tile: Float = 0f) {
        setCameraUniforms(s, width, height, zoom, pan)
        if (surface == TableSurface.ELM_WOOD) setElmUniforms(s, tableMm(tile, height)) else s.setFloatUniform("mode", surface.ordinal.toFloat())
    }

    /** A flat swatch of [surface] lit warm in the middle, for Settings: no shadows or fire. */
    fun swatch(surface: TableSurface, width: Float, height: Float): ShaderBrush {
        val s = swatchShaders.getOrPut(surface) { RuntimeShader(swatchAgsl(surface)) }
        setSwatchUniforms(s, surface, width, height)
        return ShaderBrush(s)
    }

    private val swatchShaders = HashMap<TableSurface, RuntimeShader>()

    private fun swatchAgsl(surface: TableSurface) = if (surface == TableSurface.ELM_WOOD) ELM_SWATCH_AGSL else SWATCH_AGSL

    private fun setSwatchUniforms(s: RuntimeShader, surface: TableSurface, width: Float, height: Float) {
        s.setFloatUniform("res", width, height)
        if (surface == TableSurface.ELM_WOOD) setElmUniforms(s, USUAL_TABLE_MM) else s.setFloatUniform("mode", surface.ordinal.toFloat())
    }
    private val swatchImages = HashMap<TableSurface, ImageBitmap>()

    /** [surface]'s swatch as made by [prerenderSwatch], or null if it hasn't been. */
    fun swatchImage(surface: TableSurface): ImageBitmap? = swatchImages[surface]

    /**
     * Renders [surface]'s swatch once on the GPU into an image for Settings (SettingsPreviews), so
     * its shader isn't compiled and run as Settings opens. If that fails, Settings draws the shader.
     */
    fun prerenderSwatch(surface: TableSurface, width: Int, height: Int) {
        if (surface in swatchImages || width <= 0 || height <= 0) return
        val shader = RuntimeShader(swatchAgsl(surface))
        setSwatchUniforms(shader, surface, width.toFloat(), height.toFloat())
        val reader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        try {
            val node = RenderNode("swatch").apply { setPosition(0, 0, width, height) }
            node.beginRecording().drawRect(0f, 0f, width.toFloat(), height.toFloat(), android.graphics.Paint().apply { this.shader = shader })
            node.endRecording()
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            reader.acquireNextImage()?.use { image ->
                image.hardwareBuffer?.use { buffer ->
                    android.graphics.Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))
                        ?.let { swatchImages[surface] = it.asImageBitmap() }
                }
            }
        } catch (e: RuntimeException) {
            // Left to Settings to draw
        } finally {
            renderer.destroy()
            reader.close()
        }
    }

    private const val SWATCH_AGSL = """
uniform float2 res;
uniform float mode;     // TableSurface order, as ALBEDO_AGSL
$SURFACES_AGSL
$STONE_AGSL
$SLATE_TABLE_AGSL
half4 main(float2 p) {
    // Closer than the whole table, so each pattern reads at swatch size
    float scale = 0.2;
    float2 q = float2(p.x - 0.5 * res.x, 0.5 * res.y - p.y) / res.y * scale;
    float fp = scale / res.y;
    float3 c;
    if (mode < 0.5) c = felt(q, fp);
    else if (mode < 2.5) c = straw(q * 1.1, fp * 1.1);
    else if (mode < 3.5) {
        // Relief raked by a low lamp from up the table
        float e = max(fp, 1e-5) * SLATE_SCALE;
        float3 n = stoneNormal(q * SLATE_SCALE, e, e);
        float lit = SLATE_LAMP_EVEN + (1.0 - SLATE_LAMP_EVEN) * 2.0 * max(dot(n, normalize(float3(0.0, 0.6, SLATE_LAMP_HEIGHT))), 0.0);
        c = stoneColour(q * SLATE_SCALE) * SLATE_GAIN * lit;
    } else c = silkBrocade(q, fp);
    float2 d = (p - 0.5 * res) / res.y * float2(0.4, 1.0);
    // 0.85^3: dimmed 15% three times (Gareth, 2026-11-15: "still quite bright", "darker still")
    float lamp = 0.61 * (0.7 + 1.5 * exp(-dot(d, d) * 2.5));
    return half4(half3(c * float3(1.0, 0.83, 0.62) * lamp), 1.0);
}
"""

    /** The elm wood's swatch, as [SWATCH_AGSL], its ridges raked by a low lamp. */
    private const val ELM_SWATCH_AGSL = """
uniform float2 res;
$SURFACES_AGSL
$ELM_WOOD_AGSL
half4 main(float2 p) {
    float scale = 0.2;
    float2 q = float2(p.x - 0.5 * res.x, 0.5 * res.y - p.y) / res.y * scale;
    float fp = scale / res.y;
    float3 n = elmWoodNormal(q * elmMm, fp * elmMm);
    float3 l = normalize(float3(0.0, 0.6, $RELIEF_LAMP_HEIGHT));
    float3 c = elmWood(q * elmMm, fp * elmMm) * (0.4 + max(dot(n, l), 0.0)) / (0.4 + l.z);
    float2 d = (p - 0.5 * res) / res.y * float2(0.4, 1.0);
    // Half SWATCH_AGSL's lamp: the honey wood read far the brightest ("especially the new elm",
    // "darker still", Gareth 2026-11-15)
    float lamp = 0.61 * 0.5 * (0.7 + 1.5 * exp(-dot(d, d) * 2.5));
    return half4(half3(c * float3(1.0, 0.83, 0.62) * lamp), 1.0);
}
"""

    /** The lamp pool's stretch across a [width] x [height] table, as the shaders: 1 upright, else width / height. */
    fun lampStretch(width: Float, height: Float): Float = max(1f, width / height)

    /** The lamp's reach up and down the table, as the shaders' lampDepth: 1 upright. */
    fun lampDepth(stretch: Float): Float = 1f + LAMP_DEPTH * (stretch - 1f)

    /** Strength of the lamp's and the flame's glossy reflection at plane point (u, v), for tile glare. */
    fun flameGlare(u: Float, v: Float, flame: FlameLight, stretch: Float = 1f): Float {
        val mu = (u - flame.u) * 3.4f / flame.spread
        val mv = (v - flame.v + 0.05f) * 2.2f
        // The dimmed lamp's own reflection, plus a fainter one of the flame
        return flame.lamp * lampGlare(u, v, stretch) + 0.7f * flame.power * exp(-(mu * mu + mv * mv) * 4f)
    }

    /** Lamp brightness at plane point (u, v), matching the shader: used to light the tiles. */
    fun lampLight(u: Float, v: Float, stretch: Float = 1f): Float {
        val du = (u - LAMP_U * stretch) / stretch
        val dv = (v - LAMP_V) / lampDepth(stretch) * 0.85f
        return 0.12f + 0.95f * kotlin.math.exp(-(du * du + dv * dv) * LAMP_FALLOFF)
    }

    /** Strength of the lamp's glossy reflection at plane point (u, v), for tile glare. */
    fun lampGlare(u: Float, v: Float, stretch: Float = 1f): Float {
        val mu = (u - LAMP_MIRROR_U * stretch) / stretch * 3.0f
        val mv = (v - LAMP_MIRROR_V) / lampDepth(stretch) * 1.6f
        return kotlin.math.exp(-(mu * mu + mv * mv) * 3.5f)
    }

    private const val LAMP_FALLOFF = 2.6f
}

/**
 * The lamp's place in table heights (u right, v up the table): right of and up from the middle,
 * so it lights the board at a slant. Its height sets how far shadows lean.
 */
internal const val LAMP_U = 0.14f
internal const val LAMP_V = 0.16f
internal const val LAMP_HEIGHT = 0.9f

// Share of a wide table's extra breadth the lamp's pool also gains up and down it: a sideways
// board's near and far rows fell darker than upright
internal const val LAMP_DEPTH = 0.3f

// Where a glossy surface shows the lamp's reflection from the tilted camera
private const val LAMP_MIRROR_U = LAMP_U * 0.6f
private const val LAMP_MIRROR_V = LAMP_V + 0.2f

// Lamp height for the slate's relief (table heights): low enough to rake it
private const val RELIEF_LAMP_HEIGHT = 0.3f
// Share of the lamp's light falling whichever way the slate faces
private const val SLATE_LAMP_EVEN = 0.7f
// ...and of the fire's: little, as it rakes across from low down
private const val SLATE_FIRE_EVEN = 0.15f

// Elm ridges are shallower than slate's: a lower lamp, less even light
private const val ELM_LAMP_HEIGHT = 0.2f
private const val ELM_LAMP_EVEN = 0.3f
private const val ELM_FIRE_EVEN = 0.15f
// Oiled finish: reflection throw (table heights per unit slope) and crest shine
private const val ELM_THROW = 0.35f
private const val ELM_SHEEN = 0.4f

/**
 * The menus' slate as a tabletop: scaled to the tiles as to the menus' screen, brighter (the
 * table's light falls away into the dark), its relief raked by a low lamp and by the fire.
 */
private const val SLATE_TABLE_AGSL = """
const float SLATE_SCALE = 7.0;       // slate units per table height
const float SLATE_GAIN = 2.2;
const float SLATE_LAMP_HEIGHT = $RELIEF_LAMP_HEIGHT;
const float SLATE_LAMP_EVEN = $SLATE_LAMP_EVEN;
"""


/**
 * Firelight at plane point q (table heights, v up), shared by both shaders: one warm source out of
 * view, a soft inverse-square pool, added to a dimmed lamp that keeps every tile face readable.
 * All from [FlameLight], each frame.
 *
 * With long shadows on, firelight is traced per pixel towards the flames through the board's
 * height map ([BoardShadowMap]); the fire is low, so a stack's shadow runs several tiles long.
 * Ambient occlusion is a lookup in the same map. Callers declare res (flat table size, pixels)
 * first.
 */
private const val FLAME_AGSL = """
// The lamp (LAMP_ constants): right of and up from the board, so it lights it at a slant; warm,
// about 2000 K (LampColour)
const float2 LAMP_AT = float2($LAMP_U, $LAMP_V);
// Where a glossy surface shows its reflection, as the tilted camera sees it
const float2 LAMP_MIRROR = float2($LAMP_MIRROR_U, $LAMP_MIRROR_V);
uniform float3 LAMP_ON_TABLE;  // the lamp's colour on the table, and on the tiles (LampColour)
uniform float3 LAMP_ON_TILES;
uniform float4 flame;       // u, v, power, reach (squared distance at which it halves)
uniform float3 flameColour;
uniform float lampLevel;    // the lamp's strength alongside the flame
// On a wider-than-tall table (a sideways board) the lamp is long, its pool stretched across the
// width so the board's ends aren't dark (TableSurfaceRenderer.lampStretch)
float lampStretch() { return max(1.0, res.x / res.y); }
// ...and, less, up and down it, so a sideways board's near and far rows match an upright one's
// (TableSurfaceRenderer.lampDepth)
float lampDepth() { return 1.0 + $LAMP_DEPTH * (lampStretch() - 1.0); }
float2 lampAt(float2 at) { return float2(at.x * lampStretch(), at.y); }
// q relative to lamp point [at] (given for an upright table), in lamp widths
float2 fromLamp(float2 q, float2 at) { return float2((q.x - at.x * lampStretch()) / lampStretch(), (q.y - at.y) / lampDepth()); }
uniform shader heightMap;   // the board's height map, at flat-table resolution (BoardShadowMap)
uniform shader aoMap;       // the table's ambient occlusion in green, coarser
uniform float4 mapXform;    // table pixels now to map pixels: scale x, y, offset x, y (the board may have moved)
uniform float4 shade;       // fire shadows on (1)/off, AO map pixels per map pixel, one layer's height, tallest stack's
uniform float aoStrength;   // ambient occlusion depth (0 with no map)
uniform shader tileReliefMap; // a stone set's surface map (BoardShadowMap): face normals, x, y down the table, z up, in rgb
uniform float2 tileRelief;  // present (1) or not; its pixels per height-map pixel
uniform float flameHeight;  // flame height above the table this frame, table heights
uniform float flameSpread;  // how much wider than round the fire's pool is (FlameLight.spread)
const int SHADOW_STEPS = 40;
// Steps actually taken per ray, at most SHADOW_STEPS (FireShadows: fewer on a phone near the line)
uniform float shadowSteps;
// Scales an over-bright colour into range whole, keeping its hue; per-channel clamping would clip
// red first and turn bright orange yellow-green
float3 keepHue(float3 c) {
    return c / max(1.0, max(c.r, max(c.g, c.b)));
}
float3 flameLight(float2 q) {
    float2 d = (q - flame.xy) / float2(flameSpread, 1.0);
    return flame.z / (1.0 + dot(d, d) / flame.w) * flameColour;
}
// Height-map pixel under q (table heights), wherever the board has moved since
float2 tablePixel(float2 q) {
    return float2(q.x * res.y + 0.5 * res.x, 0.5 * res.y - q.y * res.y) * mapXform.xy + mapXform.zw;
}
// Board height at q (0 on the bare table), table heights
float boardHeight(float2 q) {
    return heightMap.eval(tablePixel(q)).a * (255.0 / 31.0) * shade.z;
}
// Height at q's own pixel, unblended, so a lower tile's edge isn't lifted by a taller neighbour
float surfaceHeight(float2 q) {
    return heightMap.eval(floor(tablePixel(q)) + 0.5).a * (255.0 / 31.0) * shade.z;
}
// Ambient occlusion: darker where tiles hem the table in
float ambient(float2 q) {
    return 1.0 - aoStrength * aoMap.eval(tablePixel(q) * shade.y).g;
}
uniform float cullUnder;    // 1 when the height map matches the board as drawn: skip the table under tiles
uniform float4 boardBox;    // standing tiles' bounds in map pixels (l, t, r, b) plus a margin; left > right when none
// Wholly under a tile (2 map pixels in from every edge), so hidden by the tiles drawn over it
bool underTiles(float2 q) {
    if (cullUnder < 0.5) return false;
    float2 t = tablePixel(q);
    if (t.x < boardBox.x || t.x > boardBox.z || t.y < boardBox.y || t.y > boardBox.w) return false;
    float lowest = min(min(heightMap.eval(t + float2(-2.0, -2.0)).a, heightMap.eval(t + float2(2.0, -2.0)).a),
                       min(heightMap.eval(t + float2(-2.0, 2.0)).a, heightMap.eval(t + float2(2.0, 2.0)).a));
    return lowest >= 30.5 / 255.0;
}
// Where the segment q to q + d (0 to 1) enters and leaves the board's bounds; first > last if it
// misses
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
// Share of the fire reaching q at height h0: 1 lit, 0 full shadow. Marches towards the flames
// while the ray is below the tallest stack, taking the deepest occlusion, softened with distance
// (the fire is broad). Only steps over the board are taken, stopping at full shadow: same result
// as taking them all.
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
    // Per-pixel start, so steps don't show as bands
    float jitter = fract(52.9829189 * fract(dot(q * res.y, float2(0.06711056, 0.00583715))));
    float steps = clamp(shadowSteps, 1.0, float(SHADOW_STEPS));
    float stride = reach / steps;
    // Step i is (i + jitter) / steps along: first and last over the board, one spare each side
    float first = max(ceil(span.x * steps - jitter) - 1.0, 0.0);
    float last = min(span.y * steps - jitter + 1.0, steps - 1.0);
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
// How far the tiles' light is desaturated towards white (TileKit.neutralLight), so pale pictures
// stay whiter by the fire
uniform float neutralLight;
$FLAME_AGSL
// A stone set's pictures were lit by one lamp from this direction (up the table, a little right),
// this share of its light falling whichever way the stone faced
const float3 MADE_BY = float3(0.3505, 0.7011, 0.6210);
const float MADE_EVEN = 0.27;
// Share of the fire's light falling whichever way the stone faces: little, as on the slate table
const float TILE_FIRE_EVEN = 0.15;
// Brightness of a point facing n in light from l, against a level one
float turned(float3 n, float3 l, float even) {
    return (even + max(dot(n, l), 0.0)) / (even + max(l.z, 1e-3));
}
half4 main(float2 p) {
    float2 q = float2((p.x - 0.5 * res.x) / res.y, (0.5 * res.y - p.y) / res.y);
    // The lamp on the tiles (softer than on the table, so no face is too dark to read), plus the
    // flame; times the exposure
    float2 l = fromLamp(q, LAMP_AT) / 0.95;
    float lamp = 0.30 + 0.70 * exp(-dot(l, l) * 3.2);
    // Traced only under tiles (when the height map is current): elsewhere the layer holds only
    // the tiles' black shadows, which no light changes
    float h0 = surfaceHeight(q);
    float fire = (cullUnder > 0.5 && h0 <= 0.0) ? 1.0 : fireShadow(q, h0);
    // Stone relief: by the fire each point is lit as it faces the fire, replacing its picture's
    // baked lamp, so crests catch the flicker; by the lamp it keeps the baked light
    float fireTurn = 1.0;
    if (tileRelief.x > 0.5) {
        half4 s = tileReliefMap.eval(tablePixel(q) * tileRelief.y);
        if (s.a > 0.02) {
            float3 m = float3(s.rgb) / s.a * 2.0 - 1.0;
            // Map y runs down the table, q's up; a half-covered pixel is half level
            float3 n = normalize(mix(float3(0.0, 0.0, 1.0), float3(m.x, -m.y, m.z), s.a));
            float made = turned(n, MADE_BY, MADE_EVEN);
            fireTurn = clamp(turned(n, normalize(float3(flame.xy - q, flameHeight)), TILE_FIRE_EVEN) / made, 0.3, 3.0);
        }
    }
    // Fire shadow dims the lamp too, so shadows read deeper
    float3 c = (lampLevel * lamp * (1.0 - 0.5 * (1.0 - fire)) * LAMP_ON_TILES + flameLight(q) * fire * fireTurn)
        * 0.81;
    c = mix(c, float3(dot(c, float3(0.2126, 0.7152, 0.0722))), neutralLight);
    return half4(half3(keepHue(c)), 1.0);
}
"""


/**
 * Elm wood (Gareth, 2026-11-15): honey-brown elm boards running up the table, the grain looping
 * round eyes. Brushed and oiled, so each growth ring is a slight ridge: the surface map
 * (ELM_SURFACE_AGSL) the lamp and fire rake across.
 *
 * Gareth's picks from numbered mock-ups: round 3's 16 (rougher, less "plastic" than the first
 * designs), vertical 70 mm boards, joint 22 (the closest fit); together, 24.
 *
 * Life-size against the tiles: measures in mm, a tile 24 across (elmMm); rings about 3 mm apart.
 *
 * One field, the growth rings crossed at each point (elmRings), gives colour and surface. Rings
 * follow its contours, so it is smooth noise only: steep where they crowd, level where they open.
 */
private const val ELM_WOOD_AGSL = """
uniform float elmMm;   // mm per table height: a tile is 24 wide, as in life

const float ELM_BOARD = 70.0;     // a board's width, mm
const float ELM_FIGURE = 110.0;   // figure size, mm: about the spacing of its swirls and eyes
const float ELM_RINGS = 36.667;   // rings per figure size where the grain runs straight: 3 mm apart
// Which way the rings are crossed: nearly straight across a board (4 degrees off), so the grain
// runs up it
const float2 ELM_ACROSS = float2(0.99756, 0.06976);
const float3 ELM_PALE = float3(0.76, 0.58, 0.32);   // the pale bands' honey
const float3 ELM_LATE = float3(0.56, 0.37, 0.17);   // a ring's darker latewood in them, and the crests' line in the deep bands
const float3 ELM_DEEP = float3(0.47, 0.29, 0.12);   // the deeper bands
const float3 ELM_OPEN = float3(0.40, 0.24, 0.11);   // where the rings open right out and the wood lies level
const float ELM_RIDGE = 0.3;      // the ridges' height, mm
const float ELM_STEP = 0.15;      // how far the pale bands stand proud of the deep, mm

// Gradient noise (quintic), about -0.5 to 0.5: the rings follow its contours, so it must never kink
float2 elmTurn(float2 i) {
    float a = 6.2831853 * hash21(i);
    return float2(cos(a), sin(a));
}
float gnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = dot(elmTurn(i), f);
    float b = dot(elmTurn(i + float2(1.0, 0.0)), f - float2(1.0, 0.0));
    float c = dot(elmTurn(i + float2(0.0, 1.0)), f - float2(0.0, 1.0));
    float d = dot(elmTurn(i + float2(1.0, 1.0)), f - float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
// Smooth noise along a line, 0 to 1
float noise1(float x) {
    float i = floor(x);
    float f = x - i;
    return mix(hash11(i * 1.7 + 3.0), hash11((i + 1.0) * 1.7 + 3.0), f * f * (3.0 - 2.0 * f));
}
// The tree's flow at p (mm), in figure sizes: straight grain warped broadly, then more tightly
float2 elmFlow(float2 p) {
    float2 s = p / ELM_FIGURE;
    s += 0.5 * float2(gnoise(s * 0.55 + float2(3.1, 7.7)), gnoise(s * 0.55 + float2(11.3, 1.9)));
    s += 0.2 * float2(gnoise(s * 1.3 + float2(5.2, 9.4)), gnoise(s * 1.3 + float2(2.6, 13.8)));
    return s;
}
// Rings crossed by p (each whole number a ring's edge): across the flow, swelling strongly where
// the trunk did into the eyes the grain loops round
float elmRings(float2 p, out float2 s) {
    s = elmFlow(p);
    float n = ELM_RINGS * (dot(s, ELM_ACROSS) + 1.1 * gnoise(s * 0.8 + float2(17.0, 4.0)));
    // Uneven ring widths, in runs (never enough for a ring to turn back on itself)
    return n + 0.35 * (noise1(n * 0.83 + 11.0) - 0.5) + 0.12 * (noise1(n * 2.7 + 5.0) - 0.5);
}
// The board under p (counted across) and p's offset from its middle (lx, mm). A joint lies a
// little left of the table's middle, so one is in view zoomed in
float elmBoardAt(float2 p, out float lx) {
    float b = floor(p.x / ELM_BOARD + 0.2);
    lx = p.x - (b + 0.3) * ELM_BOARD;
    return b;
}
// p on board b as a point of the tree: each board cut from its own part, turned a little, so no
// figure runs across a joint
float2 elmOnBoard(float2 p, float b) {
    float seed = hash11(b * 1.7 + 11.0);
    float a = (seed - 0.5) * 0.3;
    float2 turned = float2(p.x * cos(a) - p.y * sin(a), p.x * sin(a) + p.y * cos(a));
    return turned + float2(seed * 400.0, hash11(b * 2.3 + 5.0) * 300.0);
}
// The rings at p (on board b) and their slope g (rings per mm), measured over a pixel (e)
float elmAt(float2 p, float e, float b, out float2 g, out float2 s) {
    float2 unused;
    float ph = elmRings(elmOnBoard(p, b), s);
    g = float2(
        elmRings(elmOnBoard(p + float2(e, 0.0), b), unused) - ph,
        elmRings(elmOnBoard(p + float2(0.0, e), b), unused) - ph) / e;
    return ph;
}
// A ring's tone, 0 deep to 1 pale: bands a few rings wide, and broader ones over them
float elmBand(float x) {
    return 0.55 * noise1(x / 5.0) + 0.45 * noise1(x / 16.5 + 40.0);
}
float elmTone(float k) {
    return elmBand(k + 0.5) + 0.22 * (hash11(k * 0.37 + 5.0) - 0.5);
}
// Brushing wears the soft earlywood at a ring's start into a groove, rising to the hard
// latewood's crest, which drops steeply into the next groove. Ring k's crest position, varied
float elmCrest(float k) {
    return 0.58 + 0.26 * hash11(k * 0.83 + 9.0);
}
// A ring's height across it (r, 0 to 1): 0 in the groove, 1 on the crest, rounded over
float elmRidge(float r, float crest) {
    return r < crest ? sin(1.5708 * r / crest) : cos(1.5708 * (r - crest) / (1.0 - crest));
}
// ...and its slope, per ring: never flat, so the light models every ring
float elmRidgeSlope(float r, float crest) {
    return r < crest
        ? 1.5708 / crest * cos(1.5708 * r / crest)
        : -1.5708 / (1.0 - crest) * sin(1.5708 * (r - crest) / (1.0 - crest));
}
// Paleness of tone [t], 0 deep to 1 pale: soft grading, slightly more pale than deep
float elmPale01(float t) {
    return smoothstep(0.2, 0.8, t + 0.1);
}

// Its colour at p (mm); fp is a pixel's size there, in mm
float3 elmWood(float2 p, float fp) {
    float2 g;
    float2 s;
    float lx;
    float b = elmBoardAt(p, lx);
    float seed = hash11(b * 1.7 + 11.0);
    // Lookup points for the board's broad and fine patterns
    float2 pb = elmOnBoard(p, b);
    float2 pg = p + seed * float2(41.0, 23.0);
    float ph = elmAt(p, max(fp, 0.02), b, g, s);
    float dens = length(g) + 1e-6;
    // Pixels per ring: sub-pixel rings give way to their band's colour (they would shimmer)
    float ringPx = 1.0 / (dens * fp);
    float see = smoothstep(1.3, 3.2, ringPx);
    float k = floor(ph);
    float r = ph - k;
    // Each ring keeps part of its own tone, softened over a pixel at ring edges; the rest follows
    // the bands
    float beside = r < 0.5 ? k - 1.0 : k + 1.0;
    float edge = 0.5 * (1.0 - smoothstep(0.0, 1.0 / ringPx, r < 0.5 ? r : 1.0 - r));
    float stepped = mix(elmTone(k), elmTone(beside), edge);
    float lie = 0.22 * (vnoise(pb / 140.0 + float2(9.0, 2.0)) - 0.5);
    float tone = elmPale01(mix(elmBand(ph), stepped, see * 0.6) + lie);
    // Pale earlywood, and a darker latewood line down the crest's drop
    float crest = elmCrest(k);
    float ridge = elmRidge(r, crest);
    float mid = 0.5 * (1.0 + crest);
    float dl = min(abs(r - mid), abs(r + 1.0 - mid)) / (0.5 * (1.0 - crest));
    float late = exp(-dl * dl) * see;
    float3 c = mix(mix(ELM_DEEP, ELM_PALE, tone), mix(ELM_DEEP * 0.45, ELM_LATE, tone), 0.45 * late);
    // Its grooves lie a little in their own shade, whatever the light
    c *= mix(0.92, 0.77 + 0.22 * ridge, see);
    // In the deep bands each crest shows a thin paler line, so their rings read too
    float dc = (r - crest) / 0.12;
    c = mix(c, ELM_LATE, 0.4 * exp(-dc * dc) * (1.0 - tone) * see);
    // Where the rings open right out the wood lies level, and darkens
    float level = smoothstep(0.16, 0.05, dens);
    c = mix(c, ELM_OPEN, 0.8 * level * (1.0 - 0.6 * tone));

    // Brushed wood is uneven: mottled patches, streaks along the rings
    float along = (s.x * ELM_ACROSS.y - s.y * ELM_ACROSS.x) * ELM_FIGURE;
    c *= 0.80 + 0.40 * (0.6 * vnoise(pb / 9.0 + float2(4.0, 8.0)) + 0.4 * vnoise(pb / 3.3 + float2(21.0, 5.0)));
    c *= 0.86 + 0.28 * vnoise(float2(ph * 1.9, along * 0.12));
    // Close up: pores (short dark pits along the rings in each groove) and fine striations; grain
    // speckle shows from further off too
    float fine = 1.0 - smoothstep(0.12, 0.35, fp);
    float coarse = 1.0 - smoothstep(0.35, 0.7, fp);
    float pit = smoothstep(0.70, 0.90, vnoise(float2(ph * 9.0, along * 1.3))) * (1.0 - 0.7 * ridge) * fine;
    c *= 1.0 - 0.5 * pit;
    c *= 1.0 + 0.22 * (vnoise(float2(ph * 7.0, along * 0.22)) - 0.5) * fine;
    c *= 1.0 + 0.16 * (vnoise(pg * 1.5) - 0.5) * coarse + 0.12 * (vnoise(pg * 5.0) - 0.5) * fine;
    // ...and the board's slow change of colour along it
    c *= 0.9 + 0.22 * vnoise(pb / 37.0 + float2(4.0, 8.0));
    // Each board a little lighter or darker, warmer or cooler, than its neighbours
    c *= (0.9 + 0.2 * hash11(b * 3.3 + 2.0)) * mix(float3(0.95, 0.97, 1.0), float3(1.05, 1.0, 0.94), seed);
    // Board joint, a close fit: a faint hairline no finer than a pixel, the barely eased edges a
    // shade darker beside it
    float d = 0.5 * ELM_BOARD - abs(lx);
    float gap = max(0.0625, 0.575 * fp);
    c *= 1.0 - 0.56 * (1.0 - smoothstep(gap * 0.5, gap * 1.5, d));
    c *= 1.0 - 0.0375 * (1.0 - smoothstep(0.0, 1.0, d));
    return c;
}

// Which way its surface faces at p (mm): x right, y up the table, z up
float3 elmWoodNormal(float2 p, float fp) {
    float2 g;
    float2 s;
    float lx;
    float b = elmBoardAt(p, lx);
    float2 pb = elmOnBoard(p, b);
    float2 pg = p + hash11(b * 1.7 + 11.0) * float2(41.0, 23.0);
    float ph = elmAt(p, max(fp, 0.02), b, g, s);
    float dens = length(g) + 1e-6;
    float ringPx = 1.0 / (dens * fp);
    float see = smoothstep(1.3, 3.2, ringPx);
    float k = floor(ph);
    float r = ph - k;
    float crest = elmCrest(k);
    float along = (s.x * ELM_ACROSS.y - s.y * ELM_ACROSS.x) * ELM_FIGURE;
    // Ridge heights vary per ring and swell and sink along it; the groove's bed is rounded over
    // a pixel
    float depth = ELM_RIDGE * (0.6 + 0.8 * hash11(k * 0.61 + 2.0)) * (0.55 + 0.9 * vnoise(float2(k * 3.7 + 1.0, along * 0.07)));
    float bed = smoothstep(0.0, 0.5 / ringPx, min(r, 1.0 - r));
    float across = depth * elmRidgeSlope(r, crest) * bed;
    // Pale bands stand slightly proud: where the tone changes between rings the drop deepens, or
    // climbs
    float lie = 0.22 * (vnoise(pb / 140.0 + float2(9.0, 2.0)) - 0.5);
    float y = clamp((r - crest) / (1.0 - crest), 0.0, 1.0);
    float down = elmPale01(elmTone(k) + lie) - elmPale01(elmTone(k + 1.0) + lie);
    across -= ELM_STEP * down * 6.0 * y * (1.0 - y) / (1.0 - crest);
    // Slope along the rings' direction (mm per ring times rings per mm)
    float slope = clamp(across * dens, -2.4, 2.4) * see;
    float2 dir = g / dens;
    float2 lean = -slope * dir;
    // Brushed fibres: fine ridges along the rings, two sizes, fading in as the view closes
    float f1 = vnoise(float2(ph * 5.0 + 0.5, along * 0.18)) - vnoise(float2(ph * 5.0 - 0.5, along * 0.18));
    float f2 = vnoise(float2(ph * 13.0 + 0.5, along * 0.4)) - vnoise(float2(ph * 13.0 - 0.5, along * 0.4));
    lean -= dir * (0.45 * f1 * smoothstep(8.0, 16.0, ringPx) + 0.3 * f2 * smoothstep(20.0, 40.0, ringPx));
    // ...and tooth: coarse grit from further off, fine grit close up
    float fine = 1.0 - smoothstep(0.12, 0.35, fp);
    float coarse = 1.0 - smoothstep(0.35, 0.7, fp);
    lean += 0.30 * (float2(vnoise(pg * 1.5 + 3.0), vnoise(pg * 1.5 + float2(31.0, 17.0))) - 0.5) * coarse;
    lean += 0.24 * (float2(vnoise(pg * 5.0), vnoise(pg * 5.0 + float2(13.0, 47.0))) - 0.5) * fine;
    // Barely eased board edges roll into the joint and catch the light
    float d = 0.5 * ELM_BOARD - abs(lx);
    lean.x += sign(lx) * 0.225 * (1.0 - smoothstep(0.0, max(0.35, 2.0 * fp), d));
    return normalize(float3(lean, 1.0));
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

// Thai straw mat, from measurements of a real woven sedge mat (strand and binding-thread
// spacing, palette, motif bands)
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
    // Strands staggered slightly so tips interleave at colour changes, while rows stay straight,
    // square to the tiles
    float off = (hash11(col * 1.37 + 5.0) - 0.5) * 0.24 * L;
    float along = (q.y + off) / L;
    float row = floor(along);
    float t = fract(along);          // 0..1 along this spindle
    float shade = 1.0 - smoothstep(0.0, max(fp / sw * 1.5, 0.08), abs(a) - 0.33);
    // Spindle outline: full width most of its length, tapering to points; reeds packed tight
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

// Silk brocade: the daily puzzles' monthly reward
float sdSeg(float2 p, float2 a, float2 b) {
    float2 pa = p - a;
    float2 ba = b - a;
    return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0));
}

// The key fret's distance field: one squared spiral per cell, touching its neighbours'
float fretD(float2 u) {
    float2 f = fract(u);
    float d = sdSeg(f, float2(0.0, 0.08), float2(0.92, 0.08));
    d = min(d, sdSeg(f, float2(0.92, 0.08), float2(0.92, 0.92)));
    d = min(d, sdSeg(f, float2(0.92, 0.92), float2(0.24, 0.92)));
    d = min(d, sdSeg(f, float2(0.24, 0.92), float2(0.24, 0.30)));
    d = min(d, sdSeg(f, float2(0.24, 0.30), float2(0.70, 0.30)));
    d = min(d, sdSeg(f, float2(0.70, 0.30), float2(0.70, 0.70)));
    return min(d, sdSeg(f, float2(0.70, 0.70), float2(0.46, 0.70)));
}

float lineMask(float d, float w, float aa) { return 1.0 - smoothstep(w - aa, w + aa, d); }

// Gareth's pick (2026-11-15, mock-up 10): midnight indigo satin, key fret in muted silver. The
// ground is warp-faced satin of fine silk running away from the viewer; the fret is floats of
// twisted metal thread across it, couched with tiny stitches and outlined by a cord
float3 silkBrocade(float2 q, float fp) {
    float3 indigo = float3(0.040, 0.050, 0.130);
    float3 silver = float3(0.165, 0.180, 0.240);
    float cell = 0.06;
    float2 u = q / cell;
    float aa = max(fp / cell * 1.2, 0.004);
    float w = 0.068;
    float d = fretD(u);
    float m = lineMask(d, w, aa);
    // Light catches the fret's far edge; its near edge casts a thin shadow on the satin
    float dFar = fretD(u + float2(0.0, 0.035));
    float dNear = fretD(u - float2(0.0, 0.05));
    float raisedLit = m * (1.0 - lineMask(dFar, w, aa));
    float shadow = (1.0 - m) * lineMask(dNear, w, aa);

    float wp = 0.0016;                         // warp thread pitch
    float pp = 0.0017;                         // weft pick pitch
    float seeW = 1.0 - smoothstep(wp * 0.25, wp * 0.7, fp);
    float i = floor(q.x / wp);
    float a = fract(q.x / wp) - 0.5;
    float crown = sqrt(max(1.0 - 4.0 * a * a, 0.0));
    float j = floor(q.y / pp);
    // An 8-end satin: each warp tied down once in 8 picks, the ties stepping 3 along
    float tie = step(mod(j + i * 3.0, 8.0), 0.5) * (1.0 - smoothstep(0.15, 0.45, abs(fract(q.y / pp) - 0.5)));
    float tone = 0.9 + 0.2 * hash11(i * 1.31 + 7.0);
    // Slubs: here and there a thread runs a little thicker and brighter for a stretch
    float slub = smoothstep(0.78, 0.92, vnoise(float2(i * 3.7, q.y * 55.0)));
    // Each thread rounded, dark in the valleys between, with silk's bright sheen along its crown
    float3 g = indigo * tone * (0.55 + 0.6 * crown) * (1.0 + 0.35 * slub);
    g += float3(0.030, 0.038, 0.075) * pow(crown, 6.0) * (0.6 + 0.8 * vnoise(float2(i * 1.9, q.y * 30.0)));
    g *= 1.0 - 0.55 * tie;
    g = mix(indigo * (0.95 + 0.1 * tone), g, seeW);
    // The silk's long sheen, in soft folds along the warp, and faint fading where it's been used
    g *= 0.82 + 0.34 * fbm(float2(q.x * 3.5, q.y * 0.9) + 3.0);
    float worn = smoothstep(0.55, 0.85, fbm(q * 1.8 + 21.0));
    g = mix(g, g * float3(1.12, 1.1, 1.02), 0.4 * worn);
    // The odd snagged warp thread, pulled a little loose and catching more light
    float snag = step(0.996, hash11(i * 0.77 + 13.0)) * smoothstep(0.6, 0.75, vnoise(float2(i, q.y * 6.0)));
    g *= 1.0 + 0.5 * snag * seeW;
    g *= 1.0 - 0.45 * shadow;

    float tp = 0.0019;
    float seeT = 1.0 - smoothstep(tp * 0.25, tp * 0.7, fp);
    float k = floor(q.y / tp);
    float b = fract(q.y / tp) - 0.5;
    float tcrown = sqrt(max(1.0 - 4.0 * b * b, 0.0));
    // The metal strip wound round its silk core: fine bright slants along every thread
    float twist = smoothstep(0.55, 0.9, sin((q.x / tp + b * 1.6 + hash11(k) * 6.0) * 3.14159 * 1.4) * 0.5 + 0.5);
    float3 t = silver * (0.6 + 0.45 * tcrown) * (0.92 + 0.16 * hash11(k * 2.1 + 3.0));
    t *= 1.0 + 0.8 * twist * tcrown * seeT;
    // Couching stitches: fine silk across the metal every so often, staggered row to row
    float sx = q.x / (tp * 9.0) + 0.5 * mod(k, 2.0);
    float stitch = (1.0 - smoothstep(0.012, 0.03, abs(fract(sx) - 0.5))) * smoothstep(0.1, 0.3, tcrown);
    t = mix(t, indigo * 1.6, 0.45 * stitch * seeT);
    t = mix(silver * 0.86, t, seeT);
    // Tarnish here and there, as old metal thread has
    t *= 0.9 + 0.18 * fbm(q * 7.0 + 31.0);
    t *= 1.0 + 0.35 * raisedLit;

    // A twisted cord outlining the fret, a shade darker than the silver
    float cord = lineMask(abs(d - w), 0.012, aa);
    float cordTwist = smoothstep(0.4, 0.9, sin((q.x + q.y) / 0.0011) * 0.5 + 0.5);
    float3 cordC = silver * 0.62 * (0.85 + 0.3 * cordTwist * seeT);

    float3 c = mix(g, t, m);
    return mix(c, cordC, cord);
}
"""
