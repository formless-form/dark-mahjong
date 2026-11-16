package com.vortessence.mahjong.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.asAndroidPath
import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import java.util.concurrent.ConcurrentHashMap
import com.vortessence.mahjong.ui.components.tileset.MahjongTiles
import com.vortessence.mahjong.ui.components.tileset.*
import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TileSuit
import com.vortessence.mahjong.ui.theme.GoldHighlight
import com.vortessence.mahjong.ui.theme.TutorialHighlight

object TileRenderer {


    /** Draws a tile: its body, face and artwork, then its sides' shading. */
    fun drawTile(
        scope: DrawScope,
        boardTile: BoardTile,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        isSelected: Boolean = false,
        isHinted: Boolean = false,
        isBlockedHighlight: Boolean = false,
        isTutorialHighlight: Boolean = false,
        highlightFreeTiles: Boolean = false,
        glare: Float = 0f,
        shadows: TileShadows = TileShadows.NONE,
        // Hint gold strength this frame, 0..1 (the hint pulses)
        hintStrength: Float = 1f,
        // Drawn shape by shape, not from the atlas: for tiles shown much larger than the board's
        // (the pair in flight), which atlas images would soften
        live: Boolean = false,
        // Darkening below the top layer (0 on top), so layers read apart
        layerShade: Float = 0f,
        // Stretches of the right and lower sides against a same-layer neighbour, as (side, from,
        // to) triples, 0 to 1 along the side (MahjongBoard.seams)
        seams: FloatArray? = null,
        // Darkening of the right and lower sides for facing away from the light
        // (MahjongBoard.sidesAway)
        rightAway: Float = 0f,
        belowAway: Float = 0f,
    ) {
        if (boardTile.isRemoved) return
        // Inset a hair, so neighbours show a fine line (about a pixel) between them
        val inset = max(TILE_GAP_MIN, width * TILE_GAP) / 2f
        val ix = x + inset
        val iy = y + inset
        val iw = width - 2f * inset
        val ih = height - 2f * inset
        drawTileBody(
            scope, boardTile, ix, iy, iw, ih, isSelected, isHinted, isBlockedHighlight,
            isTutorialHighlight, highlightFreeTiles, glare, shadows, hintStrength, live,
        )
        // Side shading is stamped from the atlas's wall masks: clipping each tile's curved sides
        // through the perspective cost most of a frame (2026-11-12). Clipped only until the atlas
        // is ready (or to compare)
        val walls = if (live || PerfSwitches.isOff("atlas") || PerfSwitches.isOff("wallstamps")) null else TileAtlas.ready(iw)
        if ((rightAway > 0f || belowAway > 0f) && !PerfSwitches.isOff("sides")) {
            if (walls != null) walls.stampSidesAway(scope, boardTile.id, ix, iy, iw, rightAway, belowAway)
            else drawSidesAway(scope, rightAway, belowAway, ix, iy, iw, ih)
        }
        if (seams != null && !PerfSwitches.isOff("seams")) {
            if (walls != null) walls.stampSeams(scope, boardTile.id, seams, ix, iy, iw, ih)
            else drawSeams(scope, seams, ix, iy, iw, ih)
        }
        // Lower layers a shade darker; a highlighted or selected tile keeps its colour true
        val highlighted = isSelected || isHinted || isBlockedHighlight || isTutorialHighlight
        if (layerShade > 0f && !highlighted && !PerfSwitches.isOff("layershade")) {
            scope.drawRoundRect(
                color = Color.Black.copy(alpha = layerShade),
                topLeft = Offset(ix, iy),
                size = Size(iw, ih),
                cornerRadius = CornerRadius(iw * 0.085f),
            )
        }
    }

    // The gap between neighbouring tiles: a share of the tile's width, at least a pixel
    private const val TILE_GAP = 0.009f
    private const val TILE_GAP_MIN = 1f

    /*
     * Shades a tile's visible sides (right, lower) along the stretch a neighbour stands against:
     * light can't reach down the crack. The face is untouched.
     */
    private fun drawSeams(
        scope: DrawScope, seams: FloatArray,
        ix: Float, iy: Float, iw: Float, ih: Float,
    ) {
        val shade = Color.Black.copy(alpha = SIDE_SHADOW)
        val depthX = iw * BoardTransform.LAYER_SHIFT_FACTOR_X
        val depthY = ih * BoardTransform.LAYER_SHIFT_FACTOR_Y
        // Within the sides' outline only. Each side's stretch and the lower-right corner are
        // separate, non-overlapping pieces, so nothing is shaded twice; a stretch ending partway
        // fades over a corner's length. The corner's shade turns from the right side's to the lower's
        val r = iw * 0.085f
        scope.withTransform({ translate(ix, iy) }) {
            clipPath(sidesOutline(iw, ih, depthX, depthY)) {
                var rightToCorner = false
                var belowToCorner = false
                var i = 0
                while (i + 2 < seams.size) {
                    val side = seams[i].toInt()
                    val from = seams[i + 1]
                    val to = seams[i + 2]
                    i += 3
                    if (to >= 1f) {
                        if (side == SEAM_RIGHT) rightToCorner = true else belowToCorner = true
                    }
                    if (side == SEAM_RIGHT) {
                        val start = from * ih
                        val end = if (to >= 1f) ih - r else to * ih
                        if (end <= start) continue
                        drawRect(
                            fadedEnds(shade, start, end, r, from > 0f, to < 1f, vertical = true),
                            Offset(iw - r, start), Size(r + depthX, end - start),
                        )
                    } else {
                        val start = from * iw
                        val end = if (to >= 1f) iw - r else to * iw
                        if (end <= start) continue
                        drawRect(
                            fadedEnds(shade, start, end, r, from > 0f, to < 1f, vertical = false),
                            Offset(start, ih - r), Size(end - start, r + depthY),
                        )
                    }
                }
                if (rightToCorner || belowToCorner) {
                    val clear = shade.copy(alpha = 0f)
                    val facingRight = if (rightToCorner) shade else clear
                    val facingDown = if (belowToCorner) shade else clear
                    drawRect(
                        Brush.sweepGradient(
                            0f to facingRight,
                            0.25f to facingDown,
                            1f to facingRight,
                            center = Offset(iw - r, ih - r),
                        ),
                        Offset(iw - r, ih - r), Size(r + depthX, r + depthY),
                    )
                }
            }
        }
    }

    /*
     * Darkens sides facing away from the light: right by [right], lower by [below], the corner
     * turning from one to the other. Within the sides' outline, as the seams.
     */
    private fun drawSidesAway(scope: DrawScope, right: Float, below: Float, ix: Float, iy: Float, iw: Float, ih: Float) {
        val depthX = iw * BoardTransform.LAYER_SHIFT_FACTOR_X
        val depthY = ih * BoardTransform.LAYER_SHIFT_FACTOR_Y
        val r = iw * 0.085f
        val toRight = Color.Black.copy(alpha = right)
        val down = Color.Black.copy(alpha = below)
        scope.withTransform({ translate(ix, iy) }) {
            clipPath(sidesOutline(iw, ih, depthX, depthY)) {
                drawRect(toRight, Offset(iw - r, 0f), Size(r + depthX, ih - r))
                drawRect(down, Offset(0f, ih - r), Size(iw - r, r + depthY))
                drawRect(
                    Brush.sweepGradient(0f to toRight, 0.25f to down, 1f to toRight, center = Offset(iw - r, ih - r)),
                    Offset(iw - r, ih - r), Size(r + depthX, r + depthY),
                )
            }
        }
    }

    // Shade from start to end, fading over [fade] at an end that falls partway along a side
    private fun fadedEnds(shade: Color, start: Float, end: Float, fade: Float, fadeIn: Boolean, fadeOut: Boolean, vertical: Boolean): Brush {
        val length = end - start
        val f = (fade / length).coerceAtMost(0.5f)
        val clear = shade.copy(alpha = 0f)
        val stops = arrayOf(
            0f to if (fadeIn) clear else shade,
            f to shade,
            1f - f to shade,
            1f to if (fadeOut) clear else shade,
        )
        return if (vertical) Brush.verticalGradient(*stops, startY = start, endY = end)
        else Brush.horizontalGradient(*stops, startX = start, endX = end)
    }

    // The sides' outline for an iw x ih tile at the origin: the tile swept down-right, less its
    // face. Cached per tile size (main thread)
    private fun sidesOutline(iw: Float, ih: Float, depthX: Float, depthY: Float): Path {
        val made = sidesPath
        if (made != null && sidesW == iw && sidesH == ih) return made
        val sides = newSidesOutline(iw, ih, depthX, depthY)
        sidesPath = sides
        sidesW = iw
        sidesH = ih
        return sides
    }

    private fun newSidesOutline(iw: Float, ih: Float, depthX: Float, depthY: Float): Path {
        val r = CornerRadius(iw * 0.085f)
        val whole = Path().apply { addRoundRect(RoundRect(0f, 0f, iw + depthX, ih + depthY, r)) }
        val face = Path().apply { addRoundRect(RoundRect(0f, 0f, iw, ih, r)) }
        return Path().apply { op(whole, face, PathOperation.Difference) }
    }

    /**
     * A visible wall as an alpha mask over the body's image (tile at [pad], [w] x [h]): right if
     * [right], else lower. Each holds its angular share of the corner, as drawSidesAway's sweep,
     * so together they cover the sides once. Stamped at a side's darkness or through a seam's
     * gradient, they shade the walls without clipping.
     */
    private fun wallMask(right: Boolean, bodyW: Int, bodyH: Int, pad: Float, w: Float, h: Float): Bitmap {
        val depthX = w * BoardTransform.LAYER_SHIFT_FACTOR_X
        val depthY = h * BoardTransform.LAYER_SHIFT_FACTOR_Y
        val r = w * 0.085f
        val bitmap = Bitmap.createBitmap(bodyW, bodyH, Bitmap.Config.ALPHA_8)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.translate(pad, pad)
        canvas.clipPath(newSidesOutline(w, h, depthX, depthY).asAndroidPath())
        // One rectangle each, weighted by one sweep about the corner's centre (clockwise from
        // facing right): the right wall whole above the corner (0.75 to 1), fading out as it turns
        // down (0 to 0.25); the lower wall the reverse, whole left of it (0.25 to 0.5). One shape,
        // so no join shows where wall meets corner
        val solid = android.graphics.Color.BLACK
        val clear = android.graphics.Color.TRANSPARENT
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = if (right) {
            android.graphics.SweepGradient(w - r, h - r, intArrayOf(solid, clear, clear, solid, solid), floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f))
        } else {
            android.graphics.SweepGradient(w - r, h - r, intArrayOf(clear, solid, solid, clear, clear), floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f))
        }
        if (right) canvas.drawRect(w - r, -1f, w + depthX + 1f, h + depthY + 1f, paint)
        else canvas.drawRect(-1f, h - r, w + depthX + 1f, h + depthY + 1f, paint)
        bitmap.setHasMipMap(true)
        return bitmap
    }

    /**
     * A seam's shade along [side] of the tile at ([ix], [iy]), [iw] x [ih], from [from] to [to], as
     * drawSeams draws it: fading where it starts or ends partway, running on through the corner
     * where it reaches the end. Clear beyond, so it can be stamped through the whole wall's mask.
     */
    private fun seamGradient(side: Int, from: Float, to: Float, ix: Float, iy: Float, iw: Float, ih: Float): android.graphics.Shader? {
        val vertical = side == SEAM_RIGHT
        val r = iw * 0.085f
        val length = if (vertical) ih else iw
        val depth = if (vertical) ih * BoardTransform.LAYER_SHIFT_FACTOR_Y else iw * BoardTransform.LAYER_SHIFT_FACTOR_X
        val toCorner = to >= 1f
        // The straight stretch as in drawSeams (to the corner's start if it runs into it)
        val start = from * length
        val stretchEnd = if (toCorner) length - r else to * length
        if (!toCorner && stretchEnd <= start) return null
        val fade = (stretchEnd - start).coerceAtLeast(0f).let { min(r, it / 2f) }
        val first = if (toCorner) min(start, length - r) else start
        // Into the corner, the shade runs on to the wall's far edge
        val last = if (toCorner) length + depth else stretchEnd
        val shade = android.graphics.Color.argb((SIDE_SHADOW * 255f).roundToInt(), 0, 0, 0)
        val clear = android.graphics.Color.TRANSPARENT
        val span = (last - first).coerceAtLeast(1e-3f)
        val fadeIn = from > 0f
        val colours = intArrayOf(if (fadeIn) clear else shade, shade, shade, if (toCorner) shade else clear)
        val stops = floatArrayOf(0f, if (fadeIn) fade / span else 0f, if (toCorner) 1f else 1f - fade / span, 1f)
        val origin = if (vertical) iy else ix
        return if (vertical) {
            android.graphics.LinearGradient(0f, origin + first, 0f, origin + last, colours, stops, android.graphics.Shader.TileMode.CLAMP)
        } else {
            android.graphics.LinearGradient(origin + first, 0f, origin + last, 0f, colours, stops, android.graphics.Shader.TileMode.CLAMP)
        }
    }

    // A tile's seam gradients, kept while its seams and position hold (they change only on a
    // match and re-fit): main thread
    private class SeamShaders(
        val seams: FloatArray, val ix: Float, val iy: Float, val iw: Float, val ih: Float,
        // Per seam: on the right side or not, and its gradient
        val right: BooleanArray, val shaders: Array<android.graphics.Shader>,
    )

    private val seamShaders = HashMap<Int, SeamShaders>()

    private fun seamShadersFor(id: Int, seams: FloatArray, ix: Float, iy: Float, iw: Float, ih: Float): SeamShaders {
        val kept = seamShaders[id]
        if (kept != null && kept.seams === seams && kept.ix == ix && kept.iy == iy && kept.iw == iw && kept.ih == ih) return kept
        val right = ArrayList<Boolean>()
        val shaders = ArrayList<android.graphics.Shader>()
        var i = 0
        while (i + 2 < seams.size) {
            val side = seams[i].toInt()
            seamGradient(side, seams[i + 1], seams[i + 2], ix, iy, iw, ih)?.let {
                right += side == SEAM_RIGHT
                shaders += it
            }
            i += 3
        }
        if (seamShaders.size > SEAM_CACHE_MAX) seamShaders.clear()
        return SeamShaders(seams, ix, iy, iw, ih, right.toBooleanArray(), shaders.toTypedArray()).also { seamShaders[id] = it }
    }

    private const val SEAM_CACHE_MAX = 512

    private var sidesPath: Path? = null
    private var sidesW = 0f
    private var sidesH = 0f

    const val SEAM_RIGHT = 1
    const val SEAM_BOTTOM = 3

    // Side darkness in the crack against a neighbour
    private const val SIDE_SHADOW = 0.2f

    /**
     * A raised tile's soft contact shadow on the tiles beneath (cheap ambient occlusion), a little
     * larger than the tile so it shows all round. The board draws a layer's contact shadows before
     * its tiles, so they fall only on the layer below.
     */
    fun drawContactShadow(scope: DrawScope, x: Float, y: Float, width: Float, height: Float) {
        if (PerfSwitches.isOff("contact")) return
        val stamp = contactStamp(width)
        val left = x + width * BoardTransform.LAYER_SHIFT_FACTOR_X * 1.4f
        val top = y + height * BoardTransform.LAYER_SHIFT_FACTOR_Y * 1.4f
        // The stamp is the blurred shape plus its blur's reach, scaled to this tile
        val pad = width * CONTACT_BLUR * CONTACT_PAD
        contactRect.set(left - pad, top - pad, left + width + pad, top + height + pad)
        scope.drawContext.canvas.nativeCanvas.drawBitmap(stamp, null, contactRect, contactPaint)
    }

    /**
     * The contact shadow's shape blurred once into an alpha image for tiles about [width] wide
     * (remade on a large size change): a stamp, not a blur per frame.
     */
    private fun contactStamp(width: Float): Bitmap {
        val made = contactBitmap
        if (made != null && width < contactWidth * 1.15f && width > contactWidth * 0.85f) return made
        val w = width.coerceAtLeast(8f)
        val h = w * BoardTransform.ASPECT_RATIO
        val blur = w * CONTACT_BLUR
        val pad = blur * CONTACT_PAD
        val bitmap = Bitmap.createBitmap(ceil(w + 2f * pad).toInt(), ceil(h + 2f * pad).toInt(), Bitmap.Config.ALPHA_8)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        val r = w * 0.085f * 1.5f
        android.graphics.Canvas(bitmap).drawRoundRect(pad, pad, pad + w, pad + h, r, r, paint)
        contactBitmap = bitmap
        contactWidth = w
        return bitmap
    }

    private fun drawTileBody(
        scope: DrawScope,
        boardTile: BoardTile,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        isSelected: Boolean = false,
        isHinted: Boolean = false,
        isBlockedHighlight: Boolean = false,
        isTutorialHighlight: Boolean = false,
        highlightFreeTiles: Boolean = false,
        glare: Float = 0f,
        shadows: TileShadows = TileShadows.NONE,
        hintStrength: Float = 1f,
        live: Boolean = false,
    ) {
        if (boardTile.isRemoved) return

        val isImportantHighlight = isSelected || isHinted || isBlockedHighlight || isTutorialHighlight
        val freeTint = highlightFreeTiles && boardTile.isFree
        // The selected tile is drawn plain, then marked in gold
        val highlight = when {
            isSelected -> Highlight.NONE
            isHinted -> Highlight.HINT
            isBlockedHighlight -> Highlight.BLOCKED
            isTutorialHighlight -> Highlight.TUTORIAL
            else -> Highlight.NONE
        }
        val highlightColor = highlight.colour ?: if (freeTint && !isImportantHighlight) FreeTint else null
        // A hinted tile's gold pulses
        val strength = if (highlight == Highlight.HINT) hintStrength.coerceIn(0f, 1f) else 1f
        val tintAlpha = if (isImportantHighlight) 0.5f * strength else 0.15f

        // A stone set is drawn only from its images: hidden until they load, not shown in its
        // stand-in colours (only for builds without the images)
        if (TileKits.current.stone && ImperialImages.available && !PerfSwitches.isOff("atlas") && TileAtlas.ready(width) == null) return

        if (!PerfSwitches.isOff("rest")) drawRestingShadows(scope, boardTile.z, x, y, width, height, shadows)
        if (isSelected) drawGoldGlow(scope, x, y, width, height)

        // Tiles are stamped from the atlas, highlighted ones too: their bodies come tinted
        if ((!live || TileKits.current.stone) && !PerfSwitches.isOff("atlas")) {
            val atlas = TileAtlas.ready(width)
            if (atlas != null &&
                atlas.stamp(scope, boardTile, x, y, width, height, highlight, freeTint && !isSelected, strength, glare, goldFace = isSelected)
            ) {
                if (isSelected) drawGoldRim(scope, x, y, width, height)
                return
            }
        }

        drawWalls(scope, x, y, width, height, highlightColor, tintAlpha)
        drawFace(scope, x, y, width, height, highlightColor, tintAlpha, freeTint && !isSelected)
        if (isSelected) drawGoldFace(scope, x, y, width, height)

        val tile = boardTile.tile
        // Terrazzo, under the paint
        drawTerrazzo(scope, TileWear.look(boardTile.id), faceRect(x, y, width, height).topLeft, faceRect(x, y, width, height).size, glints = glare)
        if (!PerfSwitches.isOff("art")) getTileVector(tile)?.let { vector ->
            drawArtwork(scope, vector, x, y, width, height, colourDots = faceColours(tile))
        }

        // Wear: grime, hairline scratches, dings, fingerprint haze
        val look = TileWear.look(boardTile.id)
        val face = faceRect(x, y, width, height)
        if (!PerfSwitches.isOff("wear")) drawWear(scope, look, face.topLeft, face.size)
        drawTone(scope, look, face.topLeft, face.size)

        // Clear glaze over the flat face: sharp lamp reflections (rippled, with a hotspot at the
        // lamp's mirror point), scratch flares and edge glints
        if (!PerfSwitches.isOff("glaze") && !TileKits.current.stone) drawGlaze(scope, look, face.topLeft, face.size, width, glare, GlazePart.ALL)
        if (isSelected) drawGoldRim(scope, x, y, width, height)
    }

    /**
     * [tile] in [kit], alone, for showing a set (Settings): drawn whole, as the atlas holds only
     * the board's set.
     */
    fun drawSample(scope: DrawScope, tile: Tile, x: Float, y: Float, width: Float, height: Float, kit: TileKit, look: Int = 0) {
        if (kit.stone) {
            // From the set's images once loaded (requested here if not)
            val images = ImperialImages.ready
            val engraving = images?.engravings?.get(tile)
            if (images != null && engraving != null) {
                val body = images.bodies[Math.floorMod(look, images.bodies.size)]
                val k = width / images.width
                sampleRect.set(x, y, x + body.width * k, y + body.height * k)
                val canvas = scope.drawContext.canvas.nativeCanvas
                canvas.drawBitmap(body, null, sampleRect, samplePaint)
                canvas.drawBitmap(engraving, null, sampleRect, samplePaint)
                return
            }
            ImperialImages.loadSoon()
        }
        drawWalls(scope, x, y, width, height, null, 0f, kit)
        drawFace(scope, x, y, width, height, null, 0f, false, kit)
        val face = faceRect(x, y, width, height)
        drawTerrazzo(scope, look, face.topLeft, face.size, glints = 0f, kit = kit)
        getTileVector(tile)?.let { drawArtwork(scope, it, x, y, width, height, kit, faceColours(tile)) }
        drawWear(scope, look, face.topLeft, face.size, kit)
        drawTone(scope, look, face.topLeft, face.size, kit)
        if (!kit.stone) drawGlaze(scope, look, face.topLeft, face.size, width, 0f, GlazePart.ALL)
    }

    private val sampleRect = RectF()
    private val samplePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /*
     * The selected tile in polished gold (chosen 2026-10-29 over a flat amber tint): a glow
     * behind, a gold-washed face, a gold rim with a glint.
     */

    // Alternating highlights and shadows, as a curved metal edge catches the lamp
    private fun goldMetal(x: Float, y: Float, w: Float, h: Float) = Brush.linearGradient(
        0f to Color(0xFFFFF3C4),
        0.2f to Color(0xFFF2C14E),
        0.42f to Color(0xFFB8860B),
        0.6f to Color(0xFFF7D774),
        0.82f to Color(0xFF9C6F12),
        1f to Color(0xFFE9C46A),
        start = Offset(x, y),
        end = Offset(x + w, y + h),
    )

    private fun drawGoldRim(scope: DrawScope, x: Float, y: Float, w: Float, h: Float) {
        val r = w * 0.085f
        val stroke = w * 0.05f
        scope.drawRoundRect(
            brush = goldMetal(x, y, w, h),
            topLeft = Offset(x + stroke / 2f, y + stroke / 2f),
            size = Size(w - stroke, h - stroke),
            cornerRadius = CornerRadius(r - stroke / 2f),
            style = Stroke(width = stroke),
        )
        // A fine glint along its inner edge
        scope.drawRoundRect(
            color = Color(0xFFFFF6D0).copy(alpha = 0.7f),
            topLeft = Offset(x + stroke, y + stroke),
            size = Size(w - 2f * stroke, h - 2f * stroke),
            cornerRadius = CornerRadius(r - stroke),
            style = Stroke(width = w * 0.008f),
        )
    }

    private fun drawGoldGlow(scope: DrawScope, x: Float, y: Float, w: Float, h: Float) {
        val stamp = glowStamp(w, h)
        // The stamp holds the glow plus its blur's reach, for tiles glowWidth wide
        val k = w / glowWidth
        val reach = glowWidth * (GLOW_GROW + GLOW_BLUR * GLOW_PAD) * k
        glowRect.set(x - reach, y - reach, x - reach + stamp.width * k, y - reach + stamp.height * k)
        scope.drawContext.canvas.nativeCanvas.drawBitmap(stamp, null, glowRect, goldGlowPaint)
    }

    /** The gold glow's shape blurred once into an alpha image for tiles about [w] wide, as [contactStamp]. */
    private fun glowStamp(w: Float, h: Float): Bitmap {
        val made = glowBitmap
        if (made != null && w < glowWidth * 1.15f && w > glowWidth * 0.85f) return made
        val tw = w.coerceAtLeast(8f)
        val th = tw * h / w
        val grow = tw * GLOW_GROW
        val blur = tw * GLOW_BLUR
        val reach = grow + blur * GLOW_PAD
        val bitmap = Bitmap.createBitmap(ceil(tw + 2f * reach).toInt(), ceil(th * 1.13f + 2f * reach).toInt(), Bitmap.Config.ALPHA_8)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }
        android.graphics.Canvas(bitmap).drawRoundRect(reach - grow, reach - grow, reach + tw + grow, reach + th * 1.13f + grow, tw * 0.15f, tw * 0.15f, paint)
        glowBitmap = bitmap
        glowWidth = tw
        return bitmap
    }


    private fun drawGoldFace(scope: DrawScope, x: Float, y: Float, w: Float, h: Float, alpha: Float = 0.30f) {
        val face = faceRect(x, y, w, h)
        scope.drawRoundRect(
            brush = goldMetal(x, y, w, h),
            topLeft = face.topLeft,
            size = face.size,
            cornerRadius = CornerRadius(w * 0.077f),
            alpha = alpha,
        )
    }


    private val goldGlowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = android.graphics.Color.argb(230, 0xF5, 0xB8, 0x2E) }
    private val glowRect = RectF()
    private var glowBitmap: Bitmap? = null
    private var glowWidth = 0f

    // Glow reach beyond the tile and blur radius, in tile widths; the blur fades out within
    // GLOW_PAD radii
    private const val GLOW_GROW = 0.07f
    private const val GLOW_BLUR = 0.12f
    private const val GLOW_PAD = 2.2f

    /**
     * A highlight's tint over walls and face: hint gold, blocked red flash, tutorial blue. The
     * atlas holds a body for each, with and without free tiles' green.
     */
    internal enum class Highlight(val colour: Color?) {
        NONE(null),
        HINT(GoldHighlight),
        BLOCKED(Color(0xFFDC2626)),
        TUTORIAL(TutorialHighlight),
    }

    /** The shadows under a tile: its resting shadow, cast from the lamp, and on the table a soft base. */
    private fun drawRestingShadows(scope: DrawScope, layerZ: Int, x: Float, y: Float, width: Float, height: Float, shadows: TileShadows) {
        val depthX = width * BoardTransform.LAYER_SHIFT_FACTOR_X
        val depthY = height * BoardTransform.LAYER_SHIFT_FACTOR_Y
        val cornerR = width * 0.085f
        // Resting shadow under the lamp, lighter where firelight reaches
        val shadowAlpha = (0.28f + (layerZ * 0.05f)).coerceAtMost(0.65f) * shadows.lampDarkness
        if (layerZ == 0) {
            scope.drawRoundRect(
                color = Color.Black.copy(alpha = 0.2f * shadows.lampDarkness),
                topLeft = Offset(x + (depthX * 1.6f), y + (depthY * 1.6f)),
                size = Size(width, height),
                cornerRadius = CornerRadius(cornerR * 1.8f, cornerR * 1.8f),
            )
        }
        scope.drawRoundRect(
            color = Color.Black.copy(alpha = shadowAlpha),
            topLeft = Offset(x + depthX + (width * 0.01f) + shadows.lamp.x, y + depthY + (height * 0.01f) + shadows.lamp.y),
            size = Size(width, height),
            cornerRadius = CornerRadius(cornerR, cornerR),
        )
    }

    /** The tile's solid body: wooden backing, then ivory sides, swept out to its depth. */
    private fun drawWalls(scope: DrawScope, x: Float, y: Float, width: Float, height: Float, highlightColor: Color?, tintAlpha: Float, kit: TileKit = TileKits.current) {
        val depthX = width * BoardTransform.LAYER_SHIFT_FACTOR_X
        val depthY = height * BoardTransform.LAYER_SHIFT_FACTOR_Y
        val cornerR = width * 0.085f
        val corner = CornerRadius(cornerR, cornerR)
        val woodDepthStart = 0.7f
        val woodSteps = if (PerfSwitches.isOff("walls")) 1 else 8
        for (i in woodSteps downTo 0) {
            val stepDepth = woodDepthStart + ((1f - woodDepthStart) * (i / woodSteps.toFloat()))
            scope.drawRoundRect(
                color = kit.backing,
                topLeft = Offset(x + (depthX * stepDepth), y + (depthY * stepDepth)),
                size = Size(width, height),
                cornerRadius = corner,
            )
        }
        if (highlightColor != null) {
            scope.drawRoundRect(
                color = highlightColor.copy(alpha = tintAlpha),
                topLeft = Offset(x + (depthX * 0.85f), y + (depthY * 0.85f)),
                size = Size(width, height),
                cornerRadius = corner,
            )
        }

        val ivorySteps = if (PerfSwitches.isOff("walls")) 1 else 16
        for (i in ivorySteps downTo 0) {
            val progress = (i / ivorySteps.toFloat()) * woodDepthStart
            val stepX = depthX * progress
            val stepY = depthY * progress

            scope.drawRoundRect(
                color = kit.side,
                topLeft = Offset(x + stepX, y + stepY),
                size = Size(width, height),
                cornerRadius = corner,
            )

            if (i == ivorySteps) {
                scope.drawRoundRect(
                    color = Color.Black.copy(alpha = 0.3f),
                    topLeft = Offset(x + stepX, y + stepY),
                    size = Size(width, height),
                    cornerRadius = corner,
                    style = Stroke(width = width * 0.012f),
                )
            }
        }
        // Walls darken towards their foot, where little light reaches (ambient occlusion). One
        // gradient under the face, so only the walls show it
        scope.drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = WALL_SHADE_ALPHA),
                startY = y + height - depthY * 0.2f,
                endY = y + height + depthY,
            ),
            topLeft = Offset(x + depthX, y + depthY),
            size = Size(width, height),
            cornerRadius = corner,
        )
        if (highlightColor != null) {
            scope.drawRoundRect(
                color = highlightColor.copy(alpha = tintAlpha),
                topLeft = Offset(x + (depthX * 0.35f), y + (depthY * 0.35f)),
                size = Size(width, height),
                cornerRadius = corner,
            )
        }
    }

    /** The face inside its bevel, where the artwork, wear and glaze go. */
    private fun faceRect(x: Float, y: Float, width: Float, height: Float): androidx.compose.ui.geometry.Rect {
        val bevelInset = width * 0.015f
        return androidx.compose.ui.geometry.Rect(x + bevelInset, y + bevelInset, x + width - bevelInset, y + height - bevelInset)
    }

    /** The face with its bevel, any highlight, and the ivory's age. */
    private fun drawFace(
        scope: DrawScope, x: Float, y: Float, width: Float, height: Float,
        highlightColor: Color?, tintAlpha: Float, freeTint: Boolean,
        kit: TileKit = TileKits.current,
    ) {
        val cornerR = width * 0.085f
        val corner = CornerRadius(cornerR, cornerR)
        val face = faceRect(x, y, width, height)
        val faceTopLeft = face.topLeft
        val faceSize = face.size
        val faceCorner = CornerRadius(cornerR * 0.9f, cornerR * 0.9f)

        val faceBrush = Brush.verticalGradient(
            colors = listOf(kit.faceTop, kit.faceBottom),
            startY = y,
            endY = y + height,
        )
        scope.drawRoundRect(
            brush = faceBrush,
            topLeft = faceTopLeft,
            size = faceSize,
            cornerRadius = faceCorner,
        )

        if (highlightColor != null) {
            scope.drawRoundRect(
                color = highlightColor.copy(alpha = tintAlpha),
                topLeft = Offset(x, y),
                size = Size(width, height),
                cornerRadius = corner,
            )
        } else {
            scope.drawRoundRect(
                color = kit.bevelHighlight.copy(alpha = kit.bevelAlpha),
                topLeft = faceTopLeft,
                size = faceSize,
                cornerRadius = faceCorner,
                style = Stroke(width = width * 0.008f),
            )
        }

        if (freeTint) {
            scope.drawRoundRect(
                color = Color(0x0C0F6B38),
                topLeft = Offset(x, y),
                size = Size(width, height),
                cornerRadius = corner,
            )
        }

        // A wider bevel: lit along the face's top edge, shaded along its foot
        if (kit.bevel > 0f) {
            val bw = width * 0.035f * kit.bevel
            scope.drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.55f), 0.18f to Color.White.copy(alpha = 0.1f),
                    0.75f to Color.Black.copy(alpha = 0.05f), 1f to Color.Black.copy(alpha = 0.35f),
                    startY = faceTopLeft.y, endY = faceTopLeft.y + faceSize.height,
                ),
                topLeft = faceTopLeft + Offset(bw / 2f, bw / 2f),
                size = Size(faceSize.width - bw, faceSize.height - bw),
                cornerRadius = CornerRadius(faceCorner.x - bw / 2f, faceCorner.y - bw / 2f),
                style = Stroke(width = bw),
            )
        }

        // The ivory's age: the same warm tint on every tile (TileWear.ageing)
        val ageing = TileWear.ageing(0)
        if (ageing > 0.01f) {
            scope.drawRoundRect(
                color = kit.ageing.copy(alpha = ageing),
                topLeft = faceTopLeft,
                size = faceSize,
                cornerRadius = faceCorner,
            )
        }
    }

    /**
     * The face's engraved artwork: paint in carved grooves, each groove's upper wall in fine
     * shadow, its lower lip catching the light.
     */
    private fun drawArtwork(
        scope: DrawScope, vector: ImageVector, x: Float, y: Float, width: Float, height: Float,
        kit: TileKit = TileKits.current, colourDots: List<Color>? = null,
    ) {
        val cx = x + (width * 0.5f)
        val cy = y + (height * (0.5f - kit.artLift))
        val innerW = width * kit.artScale
        val innerH = height * kit.artScale
        val e = width * 0.009f
        if (kit.raised) {
            // Embossed: upper edges lit, a shadow cast down-right, and a faint dark edge all round
            // to keep it clear of the face
            if (kit.edge > 0f) {
                val edgeBase = kit.edgeColour ?: kit.cutShadow
                val edgeTint = edgeBase.copy(alpha = (edgeBase.alpha * kit.edge).coerceAtMost(1f))
                val steps = if (kit.edgeReach > 0.9f) 16 else 8
                for (i in 0 until steps) {
                    val a = i * (2f * PI.toFloat() / steps)
                    drawVectorTile(scope, vector, cx + cos(a) * e * kit.edgeReach, cy + sin(a) * e * kit.edgeReach, innerW, innerH, tint = edgeTint)
                }
            }
            drawVectorTile(scope, vector, cx + e * 0.5f, cy + e * 1.4f, innerW, innerH, tint = kit.cutShadow)
            drawVectorTile(scope, vector, cx, cy - e * 0.7f, innerW, innerH, tint = kit.cutLight)
        } else {
            drawVectorTile(scope, vector, cx, cy - e * 0.7f, innerW, innerH, tint = kit.cutShadow)
            drawVectorTile(scope, vector, cx, cy + e, innerW, innerH, tint = kit.cutLight)
        }
        drawVectorTile(scope, vector, cx, cy, innerW, innerH, tint = kit.art, recolour = kit.recolour)
        if (kit.colourDots) colourDots?.let { drawColourDots(scope, it, x, y, width, height, kit) }
    }

    /** Colour-code marks ([TileKit.colourDots]) for the face's [colours], placed by [TileKit.dotPlace]. */
    private fun drawColourDots(scope: DrawScope, colours: List<Color>, x: Float, y: Float, width: Float, height: Float, kit: TileKit) {
        if (colours.isEmpty()) return
        val r = width * kit.dotSize / 2f
        val step = r * 2.7f
        fun fill(c: Color) = kit.recolour[c] ?: DOT_COLOURS[c] ?: c
        if (kit.dotPlace == DotPlace.BOTTOM_BAR) {
            // One bar, split evenly: halves for two colours, thirds for three
            val barW = width * kit.barLength
            val barH = r * 1.3f
            val left = x + (width - barW) / 2f
            val top = y + height * 0.93f - barH / 2f
            val part = barW / colours.size
            val canvas = scope.drawContext.canvas.nativeCanvas
            // A glow round each part, in its own colour
            if (kit.glow > 0f) {
                // A tight glow and a broad one (an LED's adds its light to the face)
                val halos = if (kit.led) listOf(0.8f to 1f, 2.2f to 1f, 4f to 0.7f) else listOf(0.9f to 1f, 2.4f to 0.8f)
                for ((spread, strength) in halos) {
                    val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        maskFilter = android.graphics.BlurMaskFilter(barH * spread * kit.glowSpread, android.graphics.BlurMaskFilter.Blur.NORMAL)
                        if (kit.led) blendMode = android.graphics.BlendMode.PLUS
                    }
                    colours.forEachIndexed { i, c ->
                        glowPaint.color = fill(c).copy(alpha = (strength * kit.glow).coerceIn(0f, 1f)).toArgb()
                            val out = barH * 0.3f * kit.glowSpread.coerceAtMost(1f)
                        canvas.drawRoundRect(left + i * part, top - out, left + (i + 1) * part, top + barH + out, barH, barH, glowPaint)
                    }
                }
            }
            val pill = Path().apply { addRoundRect(RoundRect(left, top, left + barW, top + barH, CornerRadius(barH / 2f, barH / 2f))) }
            val rim = barH * kit.barOutlineWidth
            val rimColour = Color.Black.copy(alpha = kit.barOutline)
            if (kit.barSplit == BarSplit.GAP) {
                // Each colour its own piece, a gap between: the ends rounded, the inner edges square
                val gap = barH * kit.barGap
                val piece = (barW - gap * (colours.size - 1)) / colours.size
                colours.forEachIndexed { i, c ->
                    val l = left + i * (piece + gap)
                    val shape = Path().apply {
                        val first = i == 0
                        val last = i == colours.lastIndex
                        val rr = CornerRadius(barH / 2f, barH / 2f)
                        addRoundRect(
                            RoundRect(
                                l, top, l + piece, top + barH,
                                topLeftCornerRadius = if (first) rr else CornerRadius.Zero,
                                topRightCornerRadius = if (last) rr else CornerRadius.Zero,
                                bottomRightCornerRadius = if (last) rr else CornerRadius.Zero,
                                bottomLeftCornerRadius = if (first) rr else CornerRadius.Zero,
                            ),
                        )
                    }
                    scope.drawPath(shape, rimColour, style = Stroke(width = rim * 2f))
                    scope.clipPath(shape) {
                        drawRect(lerp(fill(c), Color.White, kit.barLighten), Offset(l, top), Size(piece, barH))
                        drawRect(Color.White.copy(alpha = 0.28f), Offset(l, top), Size(piece, barH * 0.38f))
                    }
                }
                return
            }
            if (kit.barEmboss > 0f) {
                // Standing proud of the face: its shadow, cast down and a little right
                val drop = barH * 0.32f * kit.barEmboss
                scope.drawRoundRect(
                    Color.Black.copy(alpha = (0.45f + 0.15f * kit.barEmboss).coerceAtMost(0.8f)),
                    Offset(left - rim + drop * 0.3f, top - rim + drop),
                    Size(barW + rim * 2f, barH + rim * 2f), CornerRadius(barH / 2f + rim, barH / 2f + rim),
                )
            }
            if (kit.barEngraved) {
                // The groove's lip: catching the light below, in shade above
                scope.drawRoundRect(
                    Color.White.copy(alpha = 0.22f), Offset(left - rim, top + rim * 0.8f),
                    Size(barW + rim * 2f, barH + rim * 0.8f), CornerRadius(barH / 2f + rim, barH / 2f + rim),
                )
            }
            // A fine dark rim, so it reads on any face
            scope.drawRoundRect(
                rimColour, Offset(left - rim, top - rim),
                Size(barW + rim * 2f, barH + rim * 2f), CornerRadius(barH / 2f + rim, barH / 2f + rim),
            )
            scope.clipPath(pill) {
                fun lit(c: Color) = lerp(fill(c), Color.White, kit.barLighten)
                colours.forEachIndexed { i, c -> drawRect(lit(c), Offset(left + i * part, top), Size(part, barH)) }
                if (kit.led) {
                    // Lit from within: each colour's hot core, near white, fading to its edges
                    colours.forEachIndexed { i, c ->
                        val core = lerp(lit(c), Color.White, kit.ledCore)
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, 0.5f to core, 1f to Color.Transparent, startY = top, endY = top + barH),
                            Offset(left + i * part, top), Size(part, barH),
                        )
                    }
                } else if (kit.barEngraved) {
                    // Paint deep in a groove: the upper wall's shadow across it, a little light
                    // along its floor
                    drawRect(Color.Black.copy(alpha = 0.18f), Offset(left, top), Size(barW, barH))
                    drawRect(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.6f), 0.5f to Color.Transparent, startY = top, endY = top + barH),
                        Offset(left, top), Size(barW, barH),
                    )
                    drawRect(Color.White.copy(alpha = 0.18f), Offset(left, top + barH * 0.8f), Size(barW, barH * 0.2f))
                } else if (kit.barEmboss > 0f) {
                    // Sharp-edged relief: a hard highlight along the top, shade along the underside
                    val e = kit.barEmboss
                    drawRect(Color.White.copy(alpha = (0.3f + 0.2f * e).coerceAtMost(0.7f)), Offset(left, top), Size(barW, barH * (0.12f + 0.08f * e)))
                    drawRect(Color.Black.copy(alpha = (0.18f * e).coerceAtMost(0.45f)), Offset(left, top + barH * (1f - 0.12f - 0.1f * e)), Size(barW, barH * (0.12f + 0.1f * e)))
                } else {
                    // A sheen along its top, so it reads as a glossy inlay
                    drawRect(Color.White.copy(alpha = 0.28f), Offset(left, top), Size(barW, barH * 0.38f))
                }
                // A dark line between colours
                if (kit.barSplit == BarSplit.LINE) for (i in 1 until colours.size) {
                    val at = left + i * part
                    drawRect(rimColour, Offset(at - barH * kit.barGap / 2f, top), Size(barH * kit.barGap, barH))
                }
            }
            // A fine light rim, as round lit glass
            if (kit.barRim) {
                scope.drawRoundRect(
                    Color.White.copy(alpha = 0.55f), Offset(left, top), Size(barW, barH), CornerRadius(barH / 2f, barH / 2f),
                    style = Stroke(width = barH * 0.12f),
                )
            }
            return
        }
        val n = colours.size
        val (startX, startY, dx, dy) = when (kit.dotPlace) {
            DotPlace.LOWER_LEFT -> listOf(x + width * 0.12f, y + height * 0.925f, step, 0f)
            DotPlace.BOTTOM -> listOf(x + width / 2f - (n - 1) * step / 2f, y + height * 0.925f, step, 0f)
            DotPlace.LEFT_COLUMN -> listOf(x + width * 0.11f, y + height * 0.9f, 0f, -step)
            DotPlace.UPPER_LEFT -> listOf(x + width * 0.12f, y + height * 0.08f, step, 0f)
            DotPlace.BOTTOM_BAR -> listOf(0f, 0f, 0f, 0f)
        }
        colours.forEachIndexed { i, c ->
            val at = Offset(startX + dx * i, startY + dy * i)
            scope.drawCircle(Color.Black.copy(alpha = 0.5f), r * 1.3f, at + Offset(r * 0.15f, r * 0.25f))
            kit.dotRing?.let { scope.drawCircle(it, r * 1.28f, at) }
            scope.drawCircle(fill(c), r, at)
            scope.drawCircle(Color.White.copy(alpha = 0.35f), r * 0.4f, at - Offset(r * 0.3f, r * 0.3f))
        }
    }

    // The dots' colours: the pictures' own, brightened to read on a dark face
    private val DOT_COLOURS = mapOf(
        FaceColours.RED to Color(0xFFF0645A),
        FaceColours.GREEN to Color(0xFF3CC85A),
        FaceColours.BLUE to Color(0xFF6E8CFF),
    )

    /**
     * Tiles stamped from a few images made once, instead of some fifty shapes, gradients and paths
     * each per frame (most of a frame's work through the tilted camera). Shared by all tiles:
     *
     * - **body:** walls, outline, face and ageing, plus tinted for each highlight, each with and
     *   without free tiles' green;
     * - **artwork:** each face's, engraving included;
     * - **surface:** each of the [TileWear.LOOKS] looks' wear and the glaze's fixed parts;
     * - **glare:** each look's lamp and fire reflections, stamped as strongly as the tile catches
     *   them.
     *
     * Made off the main thread at the displayed size; remade only when tiles are shown much larger
     * or smaller (stamped scaled meanwhile). Until ready, and for the pair in flight, tiles are
     * drawn shape by shape.
     */
    object TileAtlas {
        /** On-screen pixels per flat table pixel: the zoom times the camera's nearest magnification. */
        @Volatile
        var screenScale = 1f

        /** Make images on the calling thread (tests and previews). */
        @Volatile
        var bakeInline = false

        class Baked internal constructor(
            internal val width: Int,
            // The set its images are of
            internal val kit: TileKit,
            private val pad: Int,
            // Each Highlight's, untinted first, each followed by the same with free tiles' green
            private val bodies: Array<Bitmap>,
            private val art: Map<Tile, Bitmap>,
            // Each look's terrazzo, for a set that has it
            private val terrazzos: Array<Bitmap>?,
            private val surfaces: Array<Bitmap>,
            private val glares: Array<Bitmap>,
            // The right and lower walls' masks (wallMask), over the body's image
            private val wallRight: Bitmap,
            private val wallBelow: Bitmap,
            // A set drawn from images (TileKit.stone): a body per look, each with its shape (a
            // highlight's tint is stamped through it) and wall masks. Its artwork is each face's
            // engraving, body-sized; no terrazzo, surface or glare
            private val stoneBodies: Array<Bitmap>? = null,
            private val stoneShapes: Array<Bitmap>? = null,
            private val stoneWallRight: Array<Bitmap>? = null,
            private val stoneWallBelow: Array<Bitmap>? = null,
        ) {
            // Which of a stone set's bodies the tile [id] has
            private fun stoneIndex(id: Int) = TileWear.look(id) % (stoneBodies?.size ?: 1)

            // Where the body's image (and so the walls' masks) goes for a tile at (x, y), width wide
            private fun bodyRect(x: Float, y: Float, width: Float, body: Bitmap) {
                val k = width / this.width
                stampRect.set(x - pad * k, y - pad * k, x - pad * k + body.width * k, y - pad * k + body.height * k)
            }

            /** The right and lower sides darkened by [right] and [below], for facing away from the light. */
            internal fun stampSidesAway(scope: DrawScope, id: Int, x: Float, y: Float, width: Float, right: Float, below: Float) {
                val canvas = scope.drawContext.canvas.nativeCanvas
                val toRight = stoneWallRight?.get(stoneIndex(id)) ?: wallRight
                val down = stoneWallBelow?.get(stoneIndex(id)) ?: wallBelow
                bodyRect(x, y, width, toRight)
                if (right > 0f) {
                    wallPaint.alpha = (right.coerceIn(0f, 1f) * 255f).roundToInt()
                    canvas.drawBitmap(toRight, null, stampRect, wallPaint)
                }
                if (below > 0f) {
                    wallPaint.alpha = (below.coerceIn(0f, 1f) * 255f).roundToInt()
                    canvas.drawBitmap(down, null, stampRect, wallPaint)
                }
            }

            /** The tile's seams ([MahjongBoard]'s), each its wall's mask stamped through its gradient. */
            internal fun stampSeams(scope: DrawScope, id: Int, seams: FloatArray, x: Float, y: Float, width: Float, height: Float) {
                val shaders = seamShadersFor(id, seams, x, y, width, height)
                if (shaders.shaders.isEmpty()) return
                val canvas = scope.drawContext.canvas.nativeCanvas
                val toRight = stoneWallRight?.get(stoneIndex(id)) ?: wallRight
                val down = stoneWallBelow?.get(stoneIndex(id)) ?: wallBelow
                bodyRect(x, y, width, toRight)
                for (i in shaders.shaders.indices) {
                    seamPaint.shader = shaders.shaders[i]
                    canvas.drawBitmap(if (shaders.right[i]) toRight else down, null, stampRect, seamPaint)
                }
                seamPaint.shader = null
            }

            /**
             * Stamps [tile] at ([x], [y]), its body tinted by [highlight] as strongly as
             * [strength] (and with free tiles' green if [free]), the selection's gold over its
             * face if [goldFace]; false if this atlas lacks its artwork.
             */
            internal fun stamp(
                scope: DrawScope,
                tile: BoardTile,
                x: Float,
                y: Float,
                width: Float,
                height: Float,
                highlight: Highlight,
                free: Boolean,
                strength: Float,
                glare: Float,
                goldFace: Boolean,
            ): Boolean {
                val art = art[tile.tile] ?: return false
                val canvas = scope.drawContext.canvas.nativeCanvas
                val k = width / this.width
                val stone = stoneBodies
                if (stone != null) {
                    // The tile's look's body, any tint through its shape, then the engraving
                    val index = stoneIndex(tile.id)
                    val body = stone[index]
                    stampRect.set(x, y, x + body.width * k, y + body.height * k)
                    canvas.drawBitmap(body, null, stampRect, stampPaint)
                    val colour = highlight.colour ?: if (free) FreeTint else null
                    val shapes = stoneShapes
                    if (colour != null && shapes != null) {
                        val alpha = if (highlight == Highlight.NONE) 0.15f else 0.5f * strength
                        shapePaint.color = colour.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
                        canvas.drawBitmap(shapes[index], null, stampRect, shapePaint)
                    }
                    if (goldFace) drawGoldFace(scope, x, y, width, height)
                    stampRect.set(x, y, x + body.width * k, y + body.height * k)
                    canvas.drawBitmap(art, null, stampRect, stampPaint)
                    return true
                }
                val freeIndex = if (free) 1 else 0
                val body = bodies[highlight.ordinal * 2 + freeIndex]
                stampRect.set(x - pad * k, y - pad * k, x - pad * k + body.width * k, y - pad * k + body.height * k)
                if (highlight != Highlight.NONE && strength < 0.995f) {
                    // A tint only partly shown: the untinted body, with the tinted one over it
                    canvas.drawBitmap(bodies[freeIndex], null, stampRect, stampPaint)
                    tintPaint.alpha = (strength * 255f).roundToInt()
                    canvas.drawBitmap(body, null, stampRect, tintPaint)
                } else {
                    canvas.drawBitmap(body, null, stampRect, stampPaint)
                }
                if (goldFace) drawGoldFace(scope, x, y, width, height)
                stampRect.set(x, y, x + width, y + height)
                val look = TileWear.look(tile.id)
                if (terrazzos != null) canvas.drawBitmap(terrazzos[look], null, stampRect, stampPaint)
                canvas.drawBitmap(art, null, stampRect, stampPaint)
                canvas.drawBitmap(surfaces[look], null, stampRect, stampPaint)
                val g = glare.coerceIn(0f, 1f)
                if (g > 0.01f) {
                    glarePaint.alpha = (g * 255f).roundToInt()
                    canvas.drawBitmap(glares[look], null, stampRect, glarePaint)
                }
                return true
            }
        }

        @Volatile
        private var baked: Baked? = null

        /** Some images are made, so tiles are stamped from them (the game's usual drawing). */
        val made: Boolean get() = baked != null

        @Volatile
        private var wanted = 0
        @Volatile
        private var wantedKit: TileKit? = null
        private val worker = Executors.newSingleThreadExecutor { Thread(it, "tile-atlas").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }

        /**
         * The atlas for tiles [width] flat table pixels wide, ordering a new one if it no longer
         * suits; null while none is ready.
         */
        internal fun ready(width: Float): Baked? {
            if (!TileWear.ready) return null
            // Rounded up to whole steps, so a growing board asks for new images only now and then
            val want = (ceil(width * screenScale / STEP) * STEP).toInt().coerceIn(STEP, MAX_WIDTH)
            val now = baked
            val kit = TileKits.current
            // Images of another set (just changed in Settings) are not shown while new ones are made
            if (now != null && now.kit !== kit) baked = null
            // A stone set's images are made once, at their largest: they suit any size
            val suits = now != null && now.kit === kit && (kit.stone || (want <= now.width && want >= now.width * 0.6f))
            if (!suits && (wanted != want || wantedKit !== kit)) {
                wanted = want
                wantedKit = kit
                if (bakeInline) bake(want, kit) else worker.execute { if (wanted == want && wantedKit === kit) bake(want, kit) }
            }
            return baked
        }

        // A stone set's atlas: its images loaded, not made, plus each body's shape and wall masks
        // (the usual masks, cut to the body's stone)
        private fun bakeStone(kit: TileKit) {
            val images = ImperialImages.load() ?: return
            val w = images.width.toFloat()
            val h = w * BoardTransform.ASPECT_RATIO
            val bodies = images.bodies
            val shapes = Array(bodies.size) { bodies[it].extractAlpha().apply { setHasMipMap(true) } }
            val within = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
            fun walls(right: Boolean) = Array(bodies.size) {
                wallMask(right, bodies[it].width, bodies[it].height, 0f, w, h).also { mask ->
                    android.graphics.Canvas(mask).drawBitmap(shapes[it], 0f, 0f, within)
                }
            }
            val wallsRight = walls(right = true)
            val wallsBelow = walls(right = false)
            if (kit !== TileKits.current) return
            baked = Baked(
                images.width, kit, 0, emptyArray(), images.engravings, null, emptyArray(), emptyArray(),
                wallsRight[0], wallsBelow[0], bodies, shapes, wallsRight, wallsBelow,
            )
        }

        private fun bake(width: Int, kit: TileKit) {
            if (kit.stone) {
                bakeStone(kit)
                return
            }
            val w = width.toFloat()
            val h = ceil(w * BoardTransform.ASPECT_RATIO)
            val pad = 2
            val depthX = w * BoardTransform.LAYER_SHIFT_FACTOR_X
            val depthY = h * BoardTransform.LAYER_SHIFT_FACTOR_Y
            fun image(iw: Int, ih: Int, draw: DrawScope.() -> Unit): Bitmap {
                val bitmap = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(iw.toFloat(), ih.toFloat()), draw)
                // Mipmapped so it doesn't shimmer stamped smaller; set on the shared copy, as
                // asShared() drops it
                val shared = bitmap.asShared()
                if (shared !== bitmap) bitmap.recycle()
                shared.setHasMipMap(true)
                return shared
            }
            val bodyW = ceil(w + depthX).toInt() + 2 * pad
            val bodyH = ceil(h + depthY).toInt() + 2 * pad
            // As drawTileBody tints them: free tiles' faint green alone, or a highlight's stronger tint
            fun body(highlight: Highlight, free: Boolean) = image(bodyW, bodyH) {
                val p = pad.toFloat()
                val colour = highlight.colour ?: if (free) FreeTint else null
                val alpha = if (highlight == Highlight.NONE) 0.15f else 0.5f
                drawWalls(this, p, p, w, h, colour, alpha, kit)
                drawFace(this, p, p, w, h, colour, alpha, free, kit)
            }
            val bodies = Array(Highlight.entries.size * 2) { body(Highlight.entries[it / 2], it % 2 == 1) }
            val ih = h.toInt()
            val face = faceRect(0f, 0f, w, h)
            val art = allTiles.associateWith { tile ->
                image(width, ih) {
                    getTileVector(tile)?.let { drawArtwork(this, it, 0f, 0f, w, h, kit, faceColours(tile)) }
                }
            }
            val surfaces = Array(TileWear.LOOKS) { look ->
                image(width, ih) {
                    drawWear(this, look, face.topLeft, face.size, kit)
                    drawTone(this, look, face.topLeft, face.size, kit)
                    drawGlaze(this, look, face.topLeft, face.size, w, 0f, GlazePart.FIXED)
                }
            }
            val glares = Array(TileWear.LOOKS) { look ->
                image(width, ih) {
                    drawGlaze(this, look, face.topLeft, face.size, w, 1f, GlazePart.GLARE)
                    drawTerrazzo(this, look, face.topLeft, face.size, glints = 1f, flakes = false, kit = kit)
                }
            }
            // Terrazzo, under the paint: each look's own
            val terrazzos = if (kit.terrazzo > 0f || kit.woodGrain > 0f || kit.mottle > 0f) Array(TileWear.LOOKS) { look ->
                image(width, ih) { drawTerrazzo(this, look, face.topLeft, face.size, glints = 0f, kit = kit) }
            } else null
            val wallRight = wallMask(right = true, bodyW, bodyH, pad.toFloat(), w, h)
            val wallBelow = wallMask(right = false, bodyW, bodyH, pad.toFloat(), w, h)
            // A set changed meanwhile: these images are of the old one
            if (kit !== TileKits.current) return
            baked = Baked(width, kit, pad, bodies, art, terrazzos, surfaces, glares, wallRight, wallBelow)
        }

        // Every face once: a Season or Flower of each kind, a dragon of each colour, and so on
        private val allTiles: List<Tile> by lazy {
            TileSuit.entries.flatMap { suit -> (1..9).map { Tile(suit, it) } }.filter { getTileVector(it) != null }
        }

        private val stampRect = RectF()
        private val stampPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val tintPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val shapePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val glarePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val wallPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = android.graphics.Color.BLACK }
        private val seamPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        private const val STEP = 16
        // Enough for slightly magnified tiles; fully zoomed they soften a little, keeping the images
        // to about 30 MB
        private const val MAX_WIDTH = 256
    }

    /** Which parts of the glaze to draw: all of it, or (for the atlas) its fixed parts or its glare. */
    private enum class GlazePart { ALL, FIXED, GLARE }

    /**
     * A terrazzo face ([TileKit.terrazzo] strength): stone chips of many sizes and tones in a
     * speckled ground, [look]'s own scatter. With [glints], mica flecks catch the light that
     * strongly (the atlas keeps them in the glare image, so they twinkle with the flames);
     * [flakes] false draws only those.
     */
    private fun drawTerrazzo(scope: DrawScope, look: Int, topLeft: Offset, size: Size, glints: Float, flakes: Boolean = true, kit: TileKit = TileKits.current) {
        if (kit.woodGrain > 0f && flakes && !PerfSwitches.isOff("terrazzo")) drawWoodGrain(scope, look, topLeft, size, kit.woodGrain, kit.woodFigure)
        if (kit.mottle > 0f && flakes && !PerfSwitches.isOff("terrazzo")) drawMottle(scope, look, topLeft, size, kit)
        val strength = kit.terrazzo
        if (strength <= 0f || PerfSwitches.isOff("terrazzo")) return
        val random = kotlin.random.Random(TERRAZZO_SEED + look * 7919)
        val unit = size.width
        fun alpha(a: Float) = (a * strength).coerceIn(0f, 1f)
        val path = Path()
        scope.clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
            // Angular chips, a few large, many small, covering about two fifths of the face; each
            // mottled, some veined
            fun chip(cx: Float, cy: Float, r: Float) {
                val tone = TERRAZZO_TONES[random.nextInt(TERRAZZO_TONES.size)]
                val a = alpha(0.30f + random.nextFloat() * 0.25f)
                val corners = 4 + random.nextInt(4)
                val turn = random.nextFloat() * 6.2832f
                val stretch = 0.45f + random.nextFloat() * 0.55f
                val tilt = random.nextFloat() * 6.2832f
                val ct = cos(tilt)
                val st = sin(tilt)
                path.reset()
                for (c in 0 until corners) {
                    val t = turn + c * 6.2832f / corners + (random.nextFloat() - 0.5f) * 0.9f
                    val reach = r * (0.55f + random.nextFloat() * 0.45f)
                    val lx = cos(t) * reach
                    val ly = sin(t) * reach * stretch
                    val px = cx + lx * ct - ly * st
                    val py = cy + lx * st + ly * ct
                    if (c == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                val mottle = random.nextFloat()
                val vein = random.nextFloat() < 0.3f
                val vt = random.nextFloat() * 6.2832f
                val specks = if (r > unit * 0.02f) (r / unit * 160f).toInt() else 0
                if (flakes) drawPath(path, tone.copy(alpha = a))
                // Mottling and grain inside the chip, kept within it
                if (r > unit * 0.02f) {
                    val inside = mutableListOf<Triple<Float, Float, Boolean>>()
                    repeat(specks) {
                        inside += Triple(cx + (random.nextFloat() - 0.5f) * r * 1.4f, cy + (random.nextFloat() - 0.5f) * r * 1.4f, random.nextBoolean())
                    }
                    if (flakes) clipPath(path) {
                        drawCircle(
                            (if (mottle > 0.5f) Color.White else Color.Black).copy(alpha = alpha(0.10f)),
                            r * (0.3f + mottle * 0.4f), Offset(cx + r * 0.25f * cos(vt), cy + r * 0.25f * sin(vt)),
                        )
                        if (vein) drawLine(
                            Color.White.copy(alpha = alpha(0.16f)),
                            Offset(cx - cos(vt) * r, cy - sin(vt) * r), Offset(cx + cos(vt) * r, cy + sin(vt) * r * 0.6f),
                            strokeWidth = unit * 0.004f,
                        )
                        for ((sx, sy, pale) in inside) {
                            drawCircle((if (pale) Color.White else Color.Black).copy(alpha = alpha(0.13f)), unit * 0.0035f, Offset(sx, sy))
                        }
                    }
                }
            }
            for (i in 0 until TERRAZZO_CHIPS) {
                val big = random.nextFloat()
                // Sizes on a steep curve: grit to the odd stone a fifth of the face across
                val r = unit * (0.006f + big * big * big * big * 0.105f)
                chip(topLeft.x + random.nextFloat() * size.width, topLeft.y + random.nextFloat() * size.height, r)
            }
            // The ground's fine speckle
            for (i in 0 until TERRAZZO_SPECKS) {
                val x = topLeft.x + random.nextFloat() * size.width
                val y = topLeft.y + random.nextFloat() * size.height
                val r = unit * (0.0015f + random.nextFloat() * 0.003f)
                val kind = random.nextFloat()
                val colour = when {
                    kind < 0.4f -> Color.Black.copy(alpha = alpha(0.25f))
                    kind < 0.94f -> Color(0xFFCFCBC4).copy(alpha = alpha(0.08f + random.nextFloat() * 0.16f))
                    else -> null // mica
                }
                if (colour != null) {
                    if (flakes) drawCircle(colour, r, Offset(x, y))
                } else if (glints > 0f) {
                    drawCircle(Color(0xFFFFF8EA).copy(alpha = (0.8f * glints * strength).coerceAtMost(1f)), r * 1.2f, Offset(x, y))
                }
            }
        }
    }

    /**
     * Mottled jade ([TileKit.mottle] strength), [look]'s own: soft clouds of darker and lighter
     * green ([TileKit.mottleTones]), smaller mottles, a few pale wispy veins and fine pale flecks.
     */
    private fun drawMottle(scope: DrawScope, look: Int, topLeft: Offset, size: Size, kit: TileKit) {
        val tones = kit.mottleTones
        if (tones.size < 3) return
        val (dark, light, vein) = tones
        val strength = kit.mottle
        val random = kotlin.random.Random(MOTTLE_SEED + look * 7919)
        val unit = size.width
        fun a(x: Float) = (x * strength).coerceIn(0f, 1f)
        val clip = Path().apply {
            addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, CornerRadius(unit * 0.085f * 0.9f)))
        }
        scope.clipPath(clip) {
            // A soft cloud: a radial fade from [tone] at its heart to nothing at [r]
            fun cloud(tone: Color, alpha: Float, r: Float) {
                val c = Offset(
                    topLeft.x + (random.nextFloat() * 1.3f - 0.15f) * size.width,
                    topLeft.y + (random.nextFloat() * 1.3f - 0.15f) * size.height,
                )
                drawCircle(
                    Brush.radialGradient(
                        0f to tone.copy(alpha = a(alpha)), 0.55f to tone.copy(alpha = a(alpha * 0.5f)), 1f to Color.Transparent,
                        center = c, radius = r,
                    ),
                    r, c,
                )
            }
            // Broad clouds, then smaller mottles among them
            repeat(MOTTLE_CLOUDS) {
                cloud(if (random.nextBoolean()) dark else light, 0.45f + random.nextFloat() * 0.3f, unit * (0.2f + random.nextFloat() * 0.35f))
            }
            repeat(MOTTLE_SPOTS) {
                cloud(if (random.nextFloat() < 0.5f) dark else light, 0.35f + random.nextFloat() * 0.4f, unit * (0.02f + random.nextFloat() * 0.06f))
            }
            // Veins: pale wisps wandering across, a soft wide stroke under a finer one
            val path = Path()
            repeat(1 + random.nextInt(2)) {
                path.reset()
                var x = topLeft.x + random.nextFloat() * size.width
                var y = topLeft.y + random.nextFloat() * size.height
                var heading = random.nextFloat() * 6.2832f
                path.moveTo(x, y)
                val steps = 4 + random.nextInt(5)
                repeat(steps) {
                    heading += (random.nextFloat() - 0.5f) * 1.1f
                    val step = unit * (0.04f + random.nextFloat() * 0.05f)
                    x += cos(heading) * step
                    y += sin(heading) * step
                    path.lineTo(x, y)
                }
                val width = unit * (0.003f + random.nextFloat() * 0.005f)
                drawPath(path, vein.copy(alpha = a(0.1f)), style = Stroke(width = width * 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, vein.copy(alpha = a(0.12f + random.nextFloat() * 0.1f)), style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            // Fine pale flecks
            repeat(MOTTLE_FLECKS) {
                drawCircle(
                    vein.copy(alpha = a(0.15f + random.nextFloat() * 0.35f)),
                    unit * (0.002f + random.nextFloat() * 0.004f),
                    Offset(topLeft.x + random.nextFloat() * size.width, topLeft.y + random.nextFloat() * size.height),
                )
            }
        }
    }

    /**
     * Wood grain ([TileKit.woodGrain] strength), [look]'s own: slightly aslant lengthwise lines
     * that wave, crowd and thin; dark and pale bands; pores; on some faces a ringed knot.
     */
    private fun drawWoodGrain(scope: DrawScope, look: Int, topLeft: Offset, size: Size, strength: Float, figure: Float = 0f) {
        val random = kotlin.random.Random(WOOD_SEED + look * 15485863)
        val w = size.width
        val h = size.height
        fun alpha(a: Float) = (a * strength).coerceIn(0f, 1f)
        val tilt = (random.nextFloat() - 0.5f) * 0.25f
        // The grain's flow: a long sway, and a shorter one over it, so no two faces run alike
        val f1 = 2f + random.nextFloat() * 4f
        val a1 = w * (0.01f + random.nextFloat() * 0.03f)
        val p1 = random.nextFloat() * 6.28f
        val f2 = 7f + random.nextFloat() * 6f
        val a2 = w * (0.004f + random.nextFloat() * 0.012f) * (1f + figure)
        val p2 = random.nextFloat() * 6.28f
        // Knots: none, one, or (on figured wood) now and then two
        val knots = buildList {
            if (random.nextFloat() < 0.35f + 0.15f * figure) add(Triple(w * (0.2f + random.nextFloat() * 0.6f), h * (0.15f + random.nextFloat() * 0.7f), w * (0.05f + random.nextFloat() * 0.08f)))
            if (figure > 0f && random.nextFloat() < 0.2f * figure) add(Triple(w * (0.15f + random.nextFloat() * 0.7f), h * (0.15f + random.nextFloat() * 0.7f), w * (0.04f + random.nextFloat() * 0.05f)))
        }.map { (x, y, r) -> Triple(topLeft.x + x, topLeft.y + y, r) }
        // How far the grain at (x, y) is pushed aside by the knots
        fun push(x: Float, y: Float): Float {
            var p = 0f
            for ((kx, ky, kr) in knots) {
                val dx = x - kx
                val dy = (y - ky) * 0.6f
                val d2 = (dx * dx + dy * dy) / (kr * kr * 4f)
                p += (if (dx >= 0f) 1f else -1f) * kr * 1.6f * kotlin.math.exp(-d2)
            }
            return p
        }
        // Where a line of the grain starting at [base] runs, at height y
        fun flow(base: Float, y: Float) = base + (y - topLeft.y) * tilt + sin(y / h * f1 + p1) * a1 + sin(y / h * f2 + p2) * a2 + push(base, y)
        val path = Path()
        // A grain ribbon from [from], [width] wide, tapered by [taper] (0 to 1 along it)
        fun ribbon(from: Float, width: Float, taper: (Float) -> Float, steps: Int = 28) {
            path.reset()
            for (i in 0..steps) {
                val y = topLeft.y + h * i / steps
                if (i == 0) path.moveTo(flow(from, y), y) else path.lineTo(flow(from, y), y)
            }
            for (i in steps downTo 0) {
                val y = topLeft.y + h * i / steps
                path.lineTo(flow(from, y) + width * taper(i.toFloat() / steps), y)
            }
            path.close()
        }
        scope.clipRect(topLeft.x, topLeft.y, topLeft.x + w, topLeft.y + h) {
            // Broad bands of darker and paler wood
            var bx = topLeft.x - w * 0.2f
            while (bx < topLeft.x + w * 1.2f) {
                val bw = w * (0.05f + random.nextFloat() * 0.18f)
                val dark = random.nextBoolean()
                val a = alpha(if (dark) 0.06f + random.nextFloat() * 0.1f else 0.03f + random.nextFloat() * 0.06f)
                ribbon(bx, bw, { 1f })
                drawPath(path, (if (dark) Color.Black else Color.White).copy(alpha = a))
                bx += bw
            }
            // Figure: dark heartwood streaks that swell and thin as they wander down the face
            if (figure > 0f) {
                val streaks = 2 + random.nextInt(4)
                repeat(streaks) {
                    val from = topLeft.x + w * (-0.1f + random.nextFloat() * 1.1f)
                    val sw = w * (0.015f + random.nextFloat() * 0.06f)
                    val swell = random.nextFloat() * 6.28f
                    val swellFreq = 1.5f + random.nextFloat() * 3f
                    ribbon(from, sw, { t -> (0.25f + 0.75f * (0.5f + 0.5f * sin(t * swellFreq * 6.28f + swell))) })
                    drawPath(path, WOOD_HEART.copy(alpha = alpha((0.18f + random.nextFloat() * 0.22f) * figure)))
                }
            }
            // The grain's lines, crowding and thinning out
            val lines = 34 + random.nextInt(24)
            for (l in 0 until lines) {
                val base = topLeft.x - w * 0.2f + w * 1.4f * (l + random.nextFloat() * 0.8f) / lines
                val wobble = w * random.nextFloat() * 0.01f
                path.reset()
                val steps = 30
                for (i in 0..steps) {
                    val y = topLeft.y + h * i / steps
                    val x = flow(base, y) + sin(y / h * 13f + l) * wobble
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val dark = random.nextFloat() < 0.75f
                drawPath(
                    path, (if (dark) Color.Black else Color.White).copy(alpha = alpha(if (dark) 0.08f + random.nextFloat() * 0.2f else 0.05f + random.nextFloat() * 0.08f)),
                    style = Stroke(width = w * (0.002f + random.nextFloat() * random.nextFloat() * 0.012f)),
                )
            }
            // Each knot: rings round a dark heart
            for ((kx, ky, kr) in knots) {
                for (ring in 6 downTo 1) {
                    val rr = kr * ring / 6f * 1.5f
                    drawOval(
                        Color.Black.copy(alpha = alpha(0.1f + 0.04f * (6 - ring))),
                        Offset(kx - rr * 0.7f, ky - rr), Size(rr * 1.4f, rr * 2f), style = Stroke(width = w * 0.006f),
                    )
                }
                drawOval(WOOD_HEART.copy(alpha = alpha(0.6f)), Offset(kx - kr * 0.35f, ky - kr * 0.5f), Size(kr * 0.7f, kr))
            }
            // Pores: short dashes along the grain
            repeat(140) {
                val x = topLeft.x + random.nextFloat() * w
                val y = topLeft.y + random.nextFloat() * h
                val len = h * (0.008f + random.nextFloat() * 0.02f)
                drawLine(Color.Black.copy(alpha = alpha(0.14f)), Offset(x, y), Offset(x + len * tilt, y + len), strokeWidth = w * 0.003f)
            }
        }
    }

    /**
     * Each tile's own tone ([TileKit.toneVariation]) by [look]: slightly warmer or cooler, lighter
     * or darker, so the board reads as a real set
     */
    private fun drawTone(scope: DrawScope, look: Int, topLeft: Offset, size: Size, kit: TileKit = TileKits.current) {
        val v = kit.toneVariation
        if (v <= 0f) return
        val random = kotlin.random.Random(TONE_SEED + look * 104729)
        val warm = random.nextFloat() * 2f - 1f
        val light = random.nextFloat() * 2f - 1f
        val corner = CornerRadius(size.width * 0.085f * 0.9f, size.width * 0.085f * 0.9f)
        val warmth = if (warm > 0f) Color(0xFFC07A30) else Color(0xFF6A88A8)
        scope.drawRoundRect(warmth.copy(alpha = (abs(warm) * 0.07f * v).coerceAtMost(0.3f)), topLeft, size, corner)
        val shade = if (light > 0f) Color.White else Color.Black
        scope.drawRoundRect(shade.copy(alpha = (abs(light) * 0.06f * v).coerceAtMost(0.3f)), topLeft, size, corner)
    }

    private fun drawWear(scope: DrawScope, look: Int, topLeft: Offset, size: Size, kit: TileKit = TileKits.current) {
        val wear = TileWear.overlay(look) ?: return
        val strength = kit.wear
        if (strength <= 0f) return
        val pivot = Offset(topLeft.x + size.width / 2f, topLeft.y + size.height / 2f)
        val (sx, sy) = when (TileWear.orientation(look)) {
            1 -> -1f to 1f
            2 -> 1f to -1f
            3 -> -1f to -1f
            else -> 1f to 1f
        }
        scope.withTransform(
            transformBlock = { scale(sx, sy, pivot) },
            drawBlock = {
                drawImage(
                    image = wear,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(TileWear.WIDTH, TileWear.HEIGHT),
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    alpha = strength.coerceAtMost(1f),
                    filterQuality = FilterQuality.Medium,
                )
            },
        )
    }

    /**
     * The glaze over a face. Its fixed parts and its glare (lamp and fire reflections, [glare]
     * strong) can be drawn apart for the atlas ([part]), the glare there at full strength.
     */
    private fun drawGlaze(
        scope: DrawScope,
        look: Int,
        topLeft: Offset,
        size: Size,
        width: Float,
        glare: Float,
        part: GlazePart,
    ) {
        val cornerR = width * 0.085f
        val corner = CornerRadius(cornerR * 0.9f, cornerR * 0.9f)
        val fixed = part != GlazePart.GLARE
        // A reflection as strong as [still] whatever the light, plus [lit] times the glare
        fun reflect(still: Float, lit: Float) = when (part) {
            GlazePart.ALL -> still + lit * glare
            GlazePart.FIXED -> still
            GlazePart.GLARE -> lit
        }
        val wobbleRaw = TileWear.wobble(look)
        if (fixed) scope.drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.14f),
                0.2f to Color.White.copy(alpha = 0.03f),
                0.85f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.06f),
                startY = topLeft.y,
                endY = topLeft.y + size.height,
            ),
            topLeft = topLeft,
            size = size,
            cornerRadius = corner,
        )
        TileWear.sheen(look)?.let { sheen ->
            val mirrorX = if (TileWear.orientation(look) % 2 == 1) -1f else 1f
            scope.withTransform(
                transformBlock = { scale(mirrorX, 1f, Offset(topLeft.x + size.width / 2f, topLeft.y)) },
                drawBlock = {
                    drawImage(
                        image = sheen,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(TileWear.WIDTH, TileWear.HEIGHT),
                        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        alpha = reflect(0.5f, 0.5f).coerceAtMost(1f),
                        filterQuality = FilterQuality.Medium,
                    )
                },
            )
        }
        val band = reflect(0.05f, 0.15f)
        val bandTopLeft = Offset(topLeft.x + size.width * 0.06f, topLeft.y + size.height * 0.03f)
        val bandSize = Size(size.width * 0.88f, size.height * 0.34f)
        scope.drawOval(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = band),
                0.55f to Color.White.copy(alpha = band * 0.45f),
                1f to Color.Transparent,
                center = Offset(bandTopLeft.x + bandSize.width / 2f, bandTopLeft.y + bandSize.height * 0.42f),
                radius = bandSize.width * 0.5f,
            ),
            topLeft = bandTopLeft,
            size = bandSize,
        )
        // Hard-edged reflection of the lamp's opening; the glaze undulates, so its lower edge
        // wavers and it tilts slightly, differently per tile
        val mirror = reflect(0.17f, 0.3f)
        val mLeft = topLeft.x + size.width * 0.12f
        val mTop = topLeft.y + size.height * 0.045f
        val mW = size.width * 0.76f
        val mH = size.height * 0.145f
        val wobble = mH * (0.08f + 0.14f * wobbleRaw)
        val phase = wobbleRaw * 6.28f
        val reflection = Path().apply {
            moveTo(mLeft + mH * 0.5f, mTop)
            lineTo(mLeft + mW - mH * 0.5f, mTop)
            quadraticTo(mLeft + mW, mTop, mLeft + mW, mTop + mH * 0.5f)
            val steps = 10
            for (i in steps downTo 0) {
                val f = i / steps.toFloat()
                val px = mLeft + mW * (0.04f + 0.92f * f)
                val py = mTop + mH * 0.85f + wobble * kotlin.math.sin(f * 7.5f + phase) +
                    wobble * 0.5f * kotlin.math.sin(f * 15f + phase * 1.7f)
                lineTo(px, py)
            }
            lineTo(mLeft, mTop + mH * 0.5f)
            quadraticTo(mLeft, mTop, mLeft + mH * 0.5f, mTop)
            close()
        }
        val tilt = (wobbleRaw - 0.5f) * 5f
        scope.withTransform(
            transformBlock = { rotate(tilt, Offset(mLeft + mW / 2f, mTop + mH / 2f)) },
            drawBlock = {
                drawPath(
                    path = reflection,
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = mirror),
                        0.65f to Color.White.copy(alpha = mirror * 0.75f),
                        1f to Color.White.copy(alpha = 0f),
                        startY = mTop,
                        endY = mTop + mH + wobble,
                    ),
                )
            },
        )
        val hotGlare = if (part == GlazePart.FIXED) 0f else glare
        if (hotGlare > 0.05f) {
            val hot = Offset(topLeft.x + size.width * 0.5f, topLeft.y + size.height * 0.15f)
            scope.drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.55f * hotGlare), Color.Transparent),
                    center = hot,
                    radius = size.width * 0.22f,
                ),
                radius = size.width * 0.22f,
                center = hot,
            )
            scope.drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.75f * hotGlare),
                    0.6f to Color.White.copy(alpha = 0.35f * hotGlare),
                    1f to Color.Transparent,
                    center = hot,
                    radius = size.width * 0.07f,
                ),
                radius = size.width * 0.07f,
                center = hot,
            )
        }
        if (fixed) {
            // The clear coat ends in a fine, crisp edge glint (a flat face, not a rounded cushion)
            val rimWidth = width * 0.012f
            scope.drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.5f),
                    0.3f to Color.White.copy(alpha = 0.16f),
                    1f to Color.White.copy(alpha = 0.05f),
                    startY = topLeft.y,
                    endY = topLeft.y + size.height,
                ),
                topLeft = Offset(topLeft.x + rimWidth * 0.5f, topLeft.y + rimWidth * 0.5f),
                size = Size(size.width - rimWidth, size.height - rimWidth),
                cornerRadius = CornerRadius(corner.x - rimWidth * 0.5f, corner.y - rimWidth * 0.5f),
                style = Stroke(width = rimWidth),
            )
            // Bevel glints: bright along the top edge, softer down the left, a touch of shade below
            scope.drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(topLeft.x + cornerR, topLeft.y + width * 0.006f),
                end = Offset(topLeft.x + size.width - cornerR, topLeft.y + width * 0.006f),
                strokeWidth = width * 0.013f,
            )
            scope.drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(topLeft.x + width * 0.006f, topLeft.y + cornerR),
                end = Offset(topLeft.x + width * 0.006f, topLeft.y + size.height - cornerR),
                strokeWidth = width * 0.01f,
            )
            scope.drawLine(
                color = Color.Black.copy(alpha = 0.07f),
                start = Offset(topLeft.x + cornerR, topLeft.y + size.height - width * 0.006f),
                end = Offset(topLeft.x + size.width - cornerR, topLeft.y + size.height - width * 0.006f),
                strokeWidth = width * 0.012f,
            )
        }
    }

    internal fun getTileVector(tile: Tile): ImageVector? = when (tile.suit) {
        TileSuit.CHARACTER -> when (tile.value) {
            1 -> MahjongTiles.`08Characters1`
            2 -> MahjongTiles.`09Characters2`
            3 -> MahjongTiles.`10Characters3`
            4 -> MahjongTiles.`11Characters4`
            5 -> MahjongTiles.`12Characters5`
            6 -> MahjongTiles.`13Characters6`
            7 -> MahjongTiles.`14Characters7`
            8 -> MahjongTiles.`15Characters8`
            9 -> MahjongTiles.`16Characters9`
            else -> null
        }
        TileSuit.CIRCLE -> when (tile.value) {
            1 -> MahjongTiles.`17Circles1`
            2 -> MahjongTiles.`18Circles2`
            3 -> MahjongTiles.`19Circles3`
            4 -> MahjongTiles.`20Circles4`
            5 -> MahjongTiles.`21Circles5`
            6 -> MahjongTiles.`22Circles6`
            7 -> MahjongTiles.`23Circles7`
            8 -> MahjongTiles.`24Circles8`
            9 -> MahjongTiles.`25Circles9`
            else -> null
        }
        TileSuit.BAMBOO -> when (tile.value) {
            1 -> MahjongTiles.`26Bamboos1`
            2 -> MahjongTiles.`27Bamboos2`
            3 -> MahjongTiles.`28Bamboos3`
            4 -> MahjongTiles.`29Bamboos4`
            5 -> MahjongTiles.`30Bamboos5`
            6 -> MahjongTiles.`31Bamboos6`
            7 -> MahjongTiles.`32Bamboos7`
            8 -> MahjongTiles.`33Bamboos8`
            9 -> MahjongTiles.`34Bamboos9`
            else -> null
        }
        TileSuit.WIND -> when (tile.value) {
            1 -> MahjongTiles.`04EastWind`
            2 -> MahjongTiles.`05SouthWind`
            3 -> MahjongTiles.`06WestWind`
            4 -> MahjongTiles.`07NorthWind`
            else -> null
        }
        TileSuit.DRAGON -> when (tile.value) {
            1 -> MahjongTiles.`03RedDragon`
            2 -> MahjongTiles.`02GreenDragon`
            3 -> MahjongTiles.`01WhiteDragon`
            else -> null
        }
        TileSuit.SEASON -> when (tile.value) {
            1 -> MahjongTiles.`35Spring`
            2 -> MahjongTiles.`36Summer`
            3 -> MahjongTiles.`37Autumn`
            4 -> MahjongTiles.`38Winter`
            else -> null
        }
        TileSuit.FLOWER -> when (tile.value) {
            1 -> MahjongTiles.`39Plum`
            2 -> MahjongTiles.`40Orchid`
            3 -> MahjongTiles.`41Chrysanthemum`
            4 -> MahjongTiles.`42Bamboo`
            else -> null
        }
    }

    private sealed interface CachedVectorNode

    private class CachedGroupNode(
        val clipPath: Path?,
        val translationX: Float,
        val translationY: Float,
        val rotation: Float,
        val pivotX: Float,
        val pivotY: Float,
        val scaleX: Float,
        val scaleY: Float,
        val children: List<CachedVectorNode>,
    ) : CachedVectorNode

    private class CachedPathNode(
        val path: Path,
        val fill: Brush?,
        val fillAlpha: Float,
        val stroke: Brush?,
        val strokeAlpha: Float,
        val strokeLineWidth: Float,
        val strokeLineCap: StrokeCap,
        val strokeLineJoin: StrokeJoin,
        val strokeLineMiter: Float,
    ) : CachedVectorNode

    private val vectorCache = ConcurrentHashMap<ImageVector, CachedGroupNode>()

    private fun getOrCreateCachedVector(imageVector: ImageVector): CachedGroupNode {
        return vectorCache.computeIfAbsent(imageVector) { vector ->
            vector.root.toCachedGroup()
        }
    }

    private fun VectorGroup.toCachedGroup(): CachedGroupNode {
        val clipPath = if (clipPathData.isNotEmpty()) {
            clipPathData.toPath()
        } else null
        val childrenNodes = map { node ->
            when (node) {
                is VectorGroup -> node.toCachedGroup()
                is VectorPath -> {
                    val path = node.pathData.toPath()
                    path.fillType = node.pathFillType
                    CachedPathNode(
                        path = path,
                        fill = node.fill,
                        fillAlpha = node.fillAlpha,
                        stroke = node.stroke,
                        strokeAlpha = node.strokeAlpha,
                        strokeLineWidth = node.strokeLineWidth,
                        strokeLineCap = node.strokeLineCap,
                        strokeLineJoin = node.strokeLineJoin,
                        strokeLineMiter = node.strokeLineMiter,
                    )
                }
            }
        }
        return CachedGroupNode(
            clipPath = clipPath,
            translationX = translationX,
            translationY = translationY,
            rotation = rotation,
            pivotX = pivotX,
            pivotY = pivotY,
            scaleX = scaleX,
            scaleY = scaleY,
            children = childrenNodes
        )
    }

    /** Draws a cached vector; with [tint], every fill and stroke is drawn in that one colour. */
    private fun drawCachedNode(scope: DrawScope, node: CachedVectorNode, tint: Color? = null, recolour: Map<Color, Color> = emptyMap()) {
        fun paint(brush: Brush): Brush = when {
            tint != null -> SolidColor(tint)
            recolour.isNotEmpty() && brush is SolidColor -> recolour[brush.value]?.let { SolidColor(it) } ?: brush
            else -> brush
        }
        when (node) {
            is CachedGroupNode -> {
                scope.withTransform(
                    transformBlock = {
                        if ((node.translationX != 0f) || (node.translationY != 0f)) {
                            translate(node.translationX, node.translationY)
                        }
                        if (node.rotation != 0f) {
                            rotate(node.rotation, Offset(node.pivotX, node.pivotY))
                        }
                        if ((node.scaleX != 1f) || (node.scaleY != 1f)) {
                            scale(node.scaleX, node.scaleY, Offset(node.pivotX, node.pivotY))
                        }
                        if (node.clipPath != null) {
                            clipPath(node.clipPath)
                        }
                    },
                    drawBlock = {
                        for (child in node.children) {
                            drawCachedNode(this, child, tint, recolour)
                        }
                    }
                )
            }
            is CachedPathNode -> {
                if (node.fill != null) {
                    scope.drawPath(
                        path = node.path,
                        brush = paint(node.fill),
                        alpha = node.fillAlpha
                    )
                }
                if (node.stroke != null) {
                    scope.drawPath(
                        path = node.path,
                        brush = paint(node.stroke),
                        alpha = node.strokeAlpha,
                        style = Stroke(
                            width = node.strokeLineWidth,
                            cap = node.strokeLineCap,
                            join = node.strokeLineJoin,
                            miter = node.strokeLineMiter
                        )
                    )
                }
            }
        }
    }

    // Free tiles' green, when they are highlighted
    private val FreeTint = Color(0xFF0F6B38)

    private const val WALL_SHADE_ALPHA = 0.6f
    // Terrazzo: chip and speck counts, chip tones, and the seed for each look's scatter
    private const val TERRAZZO_CHIPS = 150
    private const val TERRAZZO_SPECKS = 900
    // Mostly paler than the ground, some darker, a little warm taupe and brown
    private val TERRAZZO_TONES = listOf(
        Color(0xFF8E8C88), Color(0xFFA9A6A0), Color(0xFF9A9894), Color(0xFFB9B5AE), Color(0xFF7D7B77),
        Color(0xFF6A6863), Color(0xFF34332F), Color(0xFF7C6D60), Color(0xFF5E4E44),
    )
    private const val TERRAZZO_SEED = 4057
    // Mottled jade: cloud, mottle and fleck counts, and seed
    private const val MOTTLE_CLOUDS = 10
    private const val MOTTLE_SPOTS = 110
    private const val MOTTLE_FLECKS = 420
    private const val MOTTLE_SEED = 8263
    private const val TONE_SEED = 7121
    private const val WOOD_SEED = 9311
    // Heartwood's dark brown, for figured wood's streaks and knots
    private val WOOD_HEART = Color(0xFF2A1608)
    private const val CONTACT_SHADOW_ALPHA = 0.45f
    private const val CONTACT_BLUR = 0.07f
    // How far past the shape the blur reaches, in blur radii
    private const val CONTACT_PAD = 3f
    private val contactPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        color = android.graphics.Color.argb((CONTACT_SHADOW_ALPHA * 255).roundToInt(), 0, 0, 0)
    }
    private val contactRect = RectF()
    private var contactBitmap: Bitmap? = null
    private var contactWidth = 0f

    internal fun drawVectorTile(
        scope: DrawScope,
        imageVector: ImageVector,
        cx: Float,
        cy: Float,
        maxW: Float,
        maxH: Float,
        tint: Color? = null,
        recolour: Map<Color, Color> = emptyMap(),
    ) {
        val scale = minOf(maxW / imageVector.viewportWidth, maxH / imageVector.viewportHeight)
        val targetW = imageVector.viewportWidth * scale
        val targetH = imageVector.viewportHeight * scale
        val drawX = cx - (targetW * 0.5f)
        val drawY = cy - (targetH * 0.5f)

        val cached = getOrCreateCachedVector(imageVector)
        scope.withTransform(
            transformBlock = {
                translate(drawX, drawY)
                scale(scale, scale, Offset.Zero)
            },
            drawBlock = {
                drawCachedNode(this, cached, tint, recolour)
            }
        )
    }

    // Each face's colours, found once
    private val faceColourCache = java.util.concurrent.ConcurrentHashMap<Tile, List<Color>>()

    /** The pictures' own colours that [tile]'s face uses, red, green, blue in that order. */
    internal fun faceColours(tile: Tile): List<Color> = faceColourCache.getOrPut(tile) { findFaceColours(tile) }

    private fun findFaceColours(tile: Tile): List<Color> {
        val vector = getTileVector(tile) ?: return emptyList()
        val found = HashSet<Color>()
        fun walk(node: CachedVectorNode) {
            when (node) {
                is CachedGroupNode -> node.children.forEach { walk(it) }
                is CachedPathNode -> listOf(node.fill, node.stroke).forEach { b -> (b as? SolidColor)?.let { found += it.value } }
            }
        }
        walk(getOrCreateCachedVector(vector))
        return FaceColours.ALL.filter { it in found }
    }
}

/**
 * A tile's resting-shadow offset under the lamp ([lamp], flat table pixels) and its darkness by
 * the fire ([lampDarkness], a share of lamp-only). The fire's long shadows are traced by the
 * shaders (BoardShadowMap).
 */
data class TileShadows(val lamp: Offset, val lampDarkness: Float = 1f) {
    companion object {
        val NONE = TileShadows(Offset.Zero)
    }
}
