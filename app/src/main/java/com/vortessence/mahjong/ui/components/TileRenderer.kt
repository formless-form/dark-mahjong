package com.vortessence.mahjong.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.nativeCanvas
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
import com.vortessence.mahjong.ui.theme.GoldAccent
import com.vortessence.mahjong.ui.theme.GoldHighlight
import com.vortessence.mahjong.ui.theme.TileBevelHighlight
import com.vortessence.mahjong.ui.theme.TileCornerIndexRed
import com.vortessence.mahjong.ui.theme.TileIvoryBottom
import com.vortessence.mahjong.ui.theme.TileIvoryTop
import com.vortessence.mahjong.ui.theme.TileSideIvoryBottom
import com.vortessence.mahjong.ui.theme.TileSideWoodBacking
import com.vortessence.mahjong.ui.theme.TutorialHighlight

object TileRenderer {

    private val cornerPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    /**
     * Draws a single Mahjong tile on Canvas with physical 3D depth, authentic enamel coloring,
     * engraved intaglio details, and corner indices faithful to classic vintage Mahjong sets.
     */
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
        showCornerIndex: Boolean = false,
        glare: Float = 0f,
        shadows: TileShadows = TileShadows.NONE,
    ) {
        if (boardTile.isRemoved) return
        // Real tiles never sit perfectly square: a fraction of a degree and a pixel or two off
        val (jx, jy, rotation) = TileWear.jitter(boardTile.id)
        scope.withTransform(
            transformBlock = {
                translate(jx * width, jy * width)
                rotate(rotation, Offset(x + width / 2f, y + height / 2f))
            },
            drawBlock = {
                drawTileBody(
                    scope, boardTile, x, y, width, height, isSelected, isHinted, isBlockedHighlight,
                isTutorialHighlight, highlightFreeTiles, showCornerIndex, glare, shadows,
                )
            },
        )
    }

    /**
     * The soft contact shadow a raised tile casts onto the tiles it rests on, deepest at its foot,
     * where little light reaches (a cheap ambient occlusion). Soft-edged and a little larger than
     * the tile, so it shows on every side. The board draws a whole layer's contact shadows before
     * that layer's tiles, so they fall only on the layer below, never on a neighbour of the same
     * height.
     */
    fun drawContactShadow(scope: DrawScope, x: Float, y: Float, width: Float, height: Float) {
        val blur = (width * 0.07f).roundToInt().coerceAtLeast(1)
        if (contactBlur != blur) {
            contactPaint.maskFilter = BlurMaskFilter(blur.toFloat(), BlurMaskFilter.Blur.NORMAL)
            contactBlur = blur
        }
        contactPaint.color = Color.Black.copy(alpha = CONTACT_SHADOW_ALPHA).toArgb()
        val left = x + width * BoardTransform.LAYER_SHIFT_FACTOR_X * 1.4f
        val top = y + height * BoardTransform.LAYER_SHIFT_FACTOR_Y * 1.4f
        val r = width * 0.085f * 1.5f
        scope.drawContext.canvas.nativeCanvas.drawRoundRect(left, top, left + width, top + height, r, r, contactPaint)
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
        showCornerIndex: Boolean = false,
        glare: Float = 0f,
        shadows: TileShadows = TileShadows.NONE,
    ) {
        if (boardTile.isRemoved) return

        val depthX = width * BoardTransform.LAYER_SHIFT_FACTOR_X
        val depthY = height * BoardTransform.LAYER_SHIFT_FACTOR_Y
        val cornerR = width * 0.085f
        val corner = CornerRadius(cornerR, cornerR)
        val layerZ = boardTile.z

        // 0. Highlight / Tint Color Logic
        val highlightColor = when {
            isSelected -> GoldAccent
            isHinted -> GoldHighlight
            isBlockedHighlight -> Color(0xFFDC2626)
            isTutorialHighlight -> TutorialHighlight
            highlightFreeTiles && boardTile.isFree -> Color(0xFF0F6B38)
            else -> null
        }
        val isImportantHighlight = isSelected || isHinted || isBlockedHighlight || isTutorialHighlight
        val tintAlpha = if (isImportantHighlight) 0.5f else 0.15f

        // 1. Physically Grounded 3D Shadows
        // The resting shadow under the lamp, filled in a little where firelight reaches
        val shadowAlpha = (0.28f + (layerZ * 0.05f)).coerceAtMost(0.65f) * shadows.lampDarkness

        // Firelight's shadow: long and soft-edged (the fire is broad). Blurred copies drawn out
        // along its length overlap most near the tile, so it fades as it reaches away.
        if (shadows.fireAlpha > 0.005f) {
            val blur = (width * 0.14f).roundToInt().coerceAtLeast(1)
            if (fireShadowBlur != blur) {
                fireShadowPaint.maskFilter = BlurMaskFilter(blur.toFloat(), BlurMaskFilter.Blur.NORMAL)
                fireShadowBlur = blur
            }
            fireShadowPaint.color = Color.Black.copy(alpha = shadows.fireAlpha * 0.3f).toArgb()
            val canvas = scope.drawContext.canvas.nativeCanvas
            for (k in 1..FIRE_SHADOW_STEPS) {
                val reach = k / FIRE_SHADOW_STEPS.toFloat()
                val left = x + depthX + shadows.fire.x * reach
                val top = y + depthY + shadows.fire.y * reach
                canvas.drawRoundRect(left, top, left + width, top + height, cornerR, cornerR, fireShadowPaint)
            }
        }

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
            cornerRadius = corner,
        )

        // 2. Solid 3D Body Extrusion (Backing -> Sides -> Face)
        // A) Bamboo / Wood Backing Layer
        val woodDepthStart = 0.7f
        val woodSteps = 8
        for (i in woodSteps downTo 0) {
            val stepDepth = woodDepthStart + ((1f - woodDepthStart) * (i / woodSteps.toFloat()))
            scope.drawRoundRect(
                color = TileSideWoodBacking,
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

        // B) Ivory Side Extrusion
        val ivorySteps = 16
        for (i in ivorySteps downTo 0) {
            val progress = (i / ivorySteps.toFloat()) * woodDepthStart
            val stepX = depthX * progress
            val stepY = depthY * progress

            scope.drawRoundRect(
                color = TileSideIvoryBottom,
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
        // C) Contact shade: the side walls darken towards their foot, where little light reaches
        // into the narrow gap against the table or the next tile (ambient occlusion). One
        // gradient under the face, so only the walls show it.
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

        // 3. Integrated Ivory Face with Beveled Edge
        val bevelInset = width * 0.015f
        val faceTopLeft = Offset(x + bevelInset, y + bevelInset)
        val faceSize = Size(width - (bevelInset * 2f), height - (bevelInset * 2f))
        val faceCorner = CornerRadius(cornerR * 0.9f, cornerR * 0.9f)

        val faceBrush = Brush.verticalGradient(
            colors = listOf(TileIvoryTop, TileIvoryBottom),
            startY = y,
            endY = y + height,
        )
        scope.drawRoundRect(
            brush = faceBrush,
            topLeft = faceTopLeft,
            size = faceSize,
            cornerRadius = faceCorner,
        )

        // 4. Face Highlights & Selection Overlay
        if (highlightColor != null) {
            scope.drawRoundRect(
                color = highlightColor.copy(alpha = tintAlpha),
                topLeft = Offset(x, y),
                size = Size(width, height),
                cornerRadius = corner,
            )
        } else {
            scope.drawRoundRect(
                color = TileBevelHighlight.copy(alpha = 0.6f),
                topLeft = faceTopLeft,
                size = faceSize,
                cornerRadius = faceCorner,
                style = Stroke(width = width * 0.008f),
            )
        }

        // Free tile subtle tint
        if (highlightFreeTiles && boardTile.isFree && !isSelected) {
            scope.drawRoundRect(
                color = Color(0x0C0F6B38),
                topLeft = Offset(x, y),
                size = Size(width, height),
                cornerRadius = corner,
            )
        }

        // Ivory ages unevenly: some tiles crisp, some warmly yellowed
        val ageing = TileWear.ageing(boardTile.id)
        if (ageing > 0.01f) {
            scope.drawRoundRect(
                color = Color(0xFFB08A4A).copy(alpha = ageing),
                topLeft = faceTopLeft,
                size = faceSize,
                cornerRadius = faceCorner,
            )
        }

        // 5. Classic Top-Left Corner Index
        val tile = boardTile.tile
        if (showCornerIndex) {
            drawCornerIndex(scope, tile, x, y, width, height)
        }

        // 6. Detailed Classic Mahjong Artwork
        val cx = x + (width * 0.5f)
        val cy = y + (height * 0.5f)
        val innerW = width * 0.85f
        val innerH = height * 0.85f

        getTileVector(tile)?.let { vector ->
            // The artwork is engraved: paint sits in carved grooves. Lit from above, each groove's
            // upper wall falls into a fine shadow and its lower lip catches a fine light edge
            val e = width * 0.009f
            drawVectorTile(scope, vector, cx, cy - e * 0.7f, innerW, innerH, tint = EngraveShadow)
            drawVectorTile(scope, vector, cx, cy + e, innerW, innerH, tint = EngraveLight)
            drawVectorTile(scope, vector, cx, cy, innerW, innerH)
        }

        // 7. Wear from years of play: grime, hairline scratches, dings, fingerprint haze
        drawWear(scope, boardTile.id, faceTopLeft, faceSize)

        // 8. The clear glaze, over everything painted on the face. The face is flat; its gloss
        // shows in sharp reflections of the lamp (streaked by tiny ripples in the glaze, strongest
        // near the lamp's mirror point, with a hotspot there), scratch flares and edge glints
        drawGlaze(scope, boardTile.id, faceTopLeft, faceSize, cornerR, faceCorner, width, glare)
    }

    private fun drawWear(scope: DrawScope, id: Int, topLeft: Offset, size: Size) {
        val wear = TileWear.overlay(id) ?: return
        val pivot = Offset(topLeft.x + size.width / 2f, topLeft.y + size.height / 2f)
        val (sx, sy) = when (TileWear.orientation(id)) {
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
                    filterQuality = FilterQuality.Medium,
                )
            },
        )
    }

    private fun drawGlaze(
        scope: DrawScope,
        id: Int,
        topLeft: Offset,
        size: Size,
        cornerR: Float,
        corner: CornerRadius,
        width: Float,
        glare: Float,
    ) {
        val (_, _, wobbleRaw) = TileWear.surface(id)
        scope.drawRoundRect(
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
        TileWear.sheen(id)?.let { sheen ->
            val mirrorX = if (TileWear.orientation(id) % 2 == 1) -1f else 1f
            scope.withTransform(
                transformBlock = { scale(mirrorX, 1f, Offset(topLeft.x + size.width / 2f, topLeft.y)) },
                drawBlock = {
                    drawImage(
                        image = sheen,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(TileWear.WIDTH, TileWear.HEIGHT),
                        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        alpha = (0.5f + 0.5f * glare).coerceAtMost(1f),
                        filterQuality = FilterQuality.Medium,
                    )
                },
            )
        }
        val band = 0.05f + 0.15f * glare
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
        // A crisp, mirror-like reflection of the lamp's opening: hard-edged like real glaze
        // Because the surface undulates, the lamp's reflection wavers along its lower edge and
        // sits at a slight angle, differently on every tile
        val mirror = 0.17f + 0.3f * glare
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
        if (glare > 0.05f) {
            val hot = Offset(topLeft.x + size.width * 0.5f, topLeft.y + size.height * 0.15f)
            scope.drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.55f * glare), Color.Transparent),
                    center = hot,
                    radius = size.width * 0.22f,
                ),
                radius = size.width * 0.22f,
                center = hot,
            )
            scope.drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.75f * glare),
                    0.6f to Color.White.copy(alpha = 0.35f * glare),
                    1f to Color.Transparent,
                    center = hot,
                    radius = size.width * 0.07f,
                ),
                radius = size.width * 0.07f,
                center = hot,
            )
        }
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

    private fun getTileVector(tile: Tile): ImageVector? = when (tile.suit) {
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

    // ==========================================
    // CORNER INDEX (Top-Left Red Notation)
    // ==========================================
    private fun drawCornerIndex(
        scope: DrawScope,
        tile: Tile,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        val label = when (tile.suit) {
            TileSuit.CHARACTER, TileSuit.CIRCLE, TileSuit.BAMBOO -> tile.value.toString()
            TileSuit.WIND -> when (tile.value) {
                1 -> "E"
                2 -> "S"
                3 -> "W"
                4 -> "N"
                else -> ""
            }
            TileSuit.SEASON -> when (tile.value) {
                1 -> "SPR"
                2 -> "SUM"
                3 -> "AUT"
                4 -> "WIN"
                else -> ""
            }
            TileSuit.FLOWER -> ""
            TileSuit.DRAGON -> when (tile.value) {
                1 -> "C"
                2 -> "F"
                3 -> "P"
                else -> ""
            }
        }

        if (label.isEmpty()) return

        cornerPaint.textSize = height * 0.12f
        cornerPaint.color = TileCornerIndexRed.toArgb()

        val cornerX = x + (width * 0.08f)
        val cornerY = y + (height * 0.155f)
        scope.drawContext.canvas.nativeCanvas.drawText(label, cornerX, cornerY, cornerPaint)
    }


    // ==========================================
    // VECTOR DRAWING RENDERER & CACHE
    // ==========================================
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
    private fun drawCachedNode(scope: DrawScope, node: CachedVectorNode, tint: Color? = null) {
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
                            drawCachedNode(this, child, tint)
                        }
                    }
                )
            }
            is CachedPathNode -> {
                if (node.fill != null) {
                    scope.drawPath(
                        path = node.path,
                        brush = if (tint != null) SolidColor(tint) else node.fill,
                        alpha = node.fillAlpha
                    )
                }
                if (node.stroke != null) {
                    scope.drawPath(
                        path = node.path,
                        brush = if (tint != null) SolidColor(tint) else node.stroke,
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

    private val EngraveShadow = Color(0x5A3A2A18)

    private const val FIRE_SHADOW_STEPS = 5
    private const val WALL_SHADE_ALPHA = 0.6f
    private val fireShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private const val CONTACT_SHADOW_ALPHA = 0.45f
    private val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var contactBlur = -1
    private var fireShadowBlur = -1
    private val EngraveLight = Color(0x99FFFDF6)

    private fun drawVectorTile(
        scope: DrawScope,
        imageVector: ImageVector,
        cx: Float,
        cy: Float,
        maxW: Float,
        maxH: Float,
        tint: Color? = null,
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
                drawCachedNode(this, cached, tint)
            }
        )
    }
}

/**
 * Where a tile's shadows fall, from the lights above the table: the lamp's ([lamp], added to the
 * tile's resting shadow, which is [lampDarkness] as dark as under the lamp alone) and the
 * firelight's ([fire], how far its far end reaches, and how dark, [fireAlpha]). In flat table
 * pixels.
 */
data class TileShadows(val lamp: Offset, val fire: Offset, val fireAlpha: Float, val lampDarkness: Float = 1f) {
    companion object {
        val NONE = TileShadows(Offset.Zero, Offset.Zero, 0f)
    }
}
