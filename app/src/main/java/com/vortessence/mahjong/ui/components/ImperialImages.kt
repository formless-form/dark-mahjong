package com.vortessence.mahjong.ui.components

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TileSuit
import java.io.IOException
import java.util.concurrent.Executors

/**
 * The Imperial images. Imperial is not drawn from shapes as the other sets are, but from images
 * that come with the app (Gareth, 2026-11-14: "ship in the app", and "in the play build only"),
 * which the game only loads and scales:
 *
 *  - a stone body for each of [BODIES] looks, its own outline, sides and all, with no engraving;
 *  - each face's engraving alone (its paint, and the cut's shade), to lay over any body;
 *  - and for each of those a surface map, smaller: which way the stone faces at each point (its
 *    direction, x to the right, y down the picture, z out of the face, in red, green and blue),
 *    a body's opaque over the whole block with its sides held level, an engraving's only where
 *    the face is cut. By these the fire's light falls on the tiles afresh each frame
 *    (BoardShadowMap, the tiles' light). They are optional: without them the set keeps the light
 *    its pictures were made in.
 *
 * All [WIDTH] pixels wide, the widest the board's atlas ever shows a tile, already lit by the
 * lamp as the other sets' tiles are when their images are made. They are in the Play build's
 * assets, in [FOLDER]. Where they are absent (the open-source build, or a build from the public
 * sources) [load] finds nothing, and the set is not offered.
 */
internal object ImperialImages {
    const val FOLDER = "imperial"
    const val WIDTH = 256
    const val BODIES = 12
    // The width of a tile in the surface maps
    const val SURFACE_WIDTH = 128

    /** The app's assets, to load from: given once at start-up (and by tests). */
    @Volatile
    var assets: AssetManager? = null

    /**
     * The set: every body is the same size, and every engraving the size of a body, to lie over
     * it. The surface maps likewise, [surfaceWidth] to a tile's width; null where the build has none.
     */
    class Images(
        val width: Int, val bodies: Array<Bitmap>, val engravings: Map<Tile, Bitmap>,
        val surfaceWidth: Int, val bodySurfaces: Array<Bitmap>?, val engravingSurfaces: Map<Tile, Bitmap>?,
    )

    /** Whether the images are loaded, as state: what is built from them is built again when they are. */
    val loaded = mutableStateOf(false)

    @Volatile
    private var images: Images? = null

    /** The images if they are loaded; null until then. */
    val ready: Images? get() = images

    /**
     * Whether this build has the images at all (the Play build does; the open-source build does
     * not): a quick look, without loading them. Where it is false the set is not to be offered.
     */
    fun present(): Boolean = try {
        assets?.list(FOLDER)?.contains(bodyName(0)) == true
    } catch (e: IOException) {
        false
    }

    fun bodyName(look: Int) = "body-$look.webp"

    fun engravingName(tile: Tile) = "${tile.suit.name.lowercase()}-${tile.value}.webp"

    fun bodySurfaceName(look: Int) = "body-$look-surface.webp"

    fun engravingSurfaceName(tile: Tile) = "${tile.suit.name.lowercase()}-${tile.value}-surface.webp"

    /** Every face once, as the atlas has them. */
    val faces: List<Tile> by lazy {
        TileSuit.entries.flatMap { suit -> (1..9).map { Tile(suit, it) } }.filter { TileRenderer.getTileVector(it) != null }
    }

    /** Loads the images on the calling thread (not the main one), once; null if any is missing. */
    @Synchronized
    fun load(): Images? {
        images?.let { return it }
        val from = assets ?: return null
        fun decode(name: String): Bitmap? = try {
            from.open("$FOLDER/$name").use { BitmapFactory.decodeStream(it) }
        } catch (e: IOException) {
            null
        }
        fun image(name: String): Bitmap? {
            val decoded = decode(name) ?: return null
            // Stamped smaller than made, it stays smooth rather than shimmering (as the atlas's own)
            val shared = decoded.asShared()
            if (shared !== decoded) decoded.recycle()
            shared.setHasMipMap(true)
            return shared
        }
        val bodies = Array(BODIES) { image(bodyName(it)) ?: return null }
        val engravings = HashMap<Tile, Bitmap>()
        for (tile in faces) engravings[tile] = image(engravingName(tile)) ?: return null
        // The surface maps: all of them, or none (they are only ever drawn into the board's own map)
        val bodySurfaces = arrayOfNulls<Bitmap>(BODIES)
        val engravingSurfaces = HashMap<Tile, Bitmap>()
        var whole = true
        for (look in 0 until BODIES) {
            bodySurfaces[look] = decode(bodySurfaceName(look))
            if (bodySurfaces[look] == null) whole = false
        }
        if (whole) for (tile in faces) {
            val surface = decode(engravingSurfaceName(tile))
            if (surface == null) {
                whole = false
                break
            }
            engravingSurfaces[tile] = surface
        }
        return Images(
            WIDTH, bodies, engravings,
            SURFACE_WIDTH, if (whole) Array(BODIES) { bodySurfaces[it]!! } else null, if (whole) engravingSurfaces else null,
        ).also {
            images = it
            loaded.value = true
        }
    }

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "imperial-images").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }

    /** Loads the images off the main thread, if they are not loaded yet. */
    fun loadSoon() {
        if (images == null) worker.execute { load() }
    }
}
