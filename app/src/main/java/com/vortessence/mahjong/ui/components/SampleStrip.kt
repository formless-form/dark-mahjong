package com.vortessence.mahjong.ui.components

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TileSuit
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A set's Settings sample tiles, drawn once into one picture. Imperial's ships ready-made
 * ([IMPERIAL_ASSET]), so Settings never loads the whole set for five tiles.
 */
object SampleStrip {
    /** Pixels; shown smaller, so it stays sharp. */
    const val TILE_HEIGHT = 320

    val SETTINGS_TILES = listOf(Tile(TileSuit.DRAGON, 1), Tile(TileSuit.CIRCLE, 5), Tile(TileSuit.BAMBOO, 3))

    /**
     * Made by ImperialSettingsSamples (a Play-build test). Lossless WebP (Gareth, 2026-11-15: no
     * artifacts; lossless AVIF showed green on the Pixel)
     */
    const val IMPERIAL_ASSET = "imperial/settings-samples.webp"

    /** Across the full width. */
    val IMPERIAL_TILES = listOf(
        Tile(TileSuit.WIND, 1), Tile(TileSuit.DRAGON, 1), Tile(TileSuit.CIRCLE, 5), Tile(TileSuit.BAMBOO, 3), Tile(TileSuit.CHARACTER, 9),
    )

    private val made = HashMap<Pair<TileKit, List<Tile>>, ImageBitmap>()
    private var imperial: ImageBitmap? = null
    private var imperialLooked = false

    /** Drawn on first request; Imperial's read from its picture (null without it). */
    fun of(kit: TileKit, tiles: List<Tile>): ImageBitmap? =
        if (kit.stone) imperial() else made.getOrPut(kit to tiles) { make(kit, tiles) }

    private fun imperial(): ImageBitmap? {
        if (!imperialLooked) {
            val assets = ImperialImages.assets ?: return null
            imperialLooked = true
            imperial = try {
                assets.open(IMPERIAL_ASSET).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            } catch (e: IOException) {
                null
            }
        }
        return imperial
    }

    /** Lamp-lit, with a margin for the tiles' sides (an Imperial stone stands proud of its outline). */
    fun make(kit: TileKit, tiles: List<Tile>): ImageBitmap {
        val h = TILE_HEIGHT.toFloat()
        val w = h / BoardTransform.ASPECT_RATIO
        val gap = w * GAP
        val m = w * MARGIN
        val width = w * tiles.size + gap * (tiles.size - 1) + 2 * m
        val height = h + 2 * m
        val picture = ImageBitmap(ceil(width).toInt(), ceil(height).toInt())
        val lamp = LampColour.tiles().let { Color(it[0], it[1], it[2]) }
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(picture), Size(width, height)) {
            tiles.forEachIndexed { i, tile -> TileRenderer.drawSample(this, tile, m + i * (w + gap), m, w, h, kit, look = i) }
            drawRect(lamp, blendMode = BlendMode.Modulate)
        }
        return picture
    }

    /** Tiles at [TILES_OF_HEIGHT] of the height, centred. */
    fun DrawScope.drawSamples(picture: ImageBitmap) {
        val k = size.height * TILES_OF_HEIGHT / TILE_HEIGHT
        val w = picture.width * k
        val h = picture.height * k
        drawImage(
            picture,
            dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }

    private const val TILES_OF_HEIGHT = 0.84f
    private const val GAP = 0.12f
    private const val MARGIN = 0.2f
}
