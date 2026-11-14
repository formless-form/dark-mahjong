package com.vortessence.mahjong.ui.components

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 *  - each face's engraving alone (its paint, and the cut's shade), to lay over any body.
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

    /** The app's assets, to load from: given once at start-up (and by tests). */
    @Volatile
    var assets: AssetManager? = null

    /** The set: every body is the same size, and every engraving the size of a body, to lie over it. */
    class Images(val width: Int, val bodies: Array<Bitmap>, val engravings: Map<Tile, Bitmap>)

    @Volatile
    private var images: Images? = null

    /** The images if they are loaded; null until then. */
    val ready: Images? get() = images

    fun bodyName(look: Int) = "body-$look.webp"

    fun engravingName(tile: Tile) = "${tile.suit.name.lowercase()}-${tile.value}.webp"

    /** Every face once, as the atlas has them. */
    val faces: List<Tile> by lazy {
        TileSuit.entries.flatMap { suit -> (1..9).map { Tile(suit, it) } }.filter { TileRenderer.getTileVector(it) != null }
    }

    /** Loads the images on the calling thread (not the main one), once; null if any is missing. */
    @Synchronized
    fun load(): Images? {
        images?.let { return it }
        val from = assets ?: return null
        fun image(name: String): Bitmap? {
            val decoded = try {
                from.open("$FOLDER/$name").use { BitmapFactory.decodeStream(it) }
            } catch (e: IOException) {
                null
            } ?: return null
            // Stamped smaller than made, it stays smooth rather than shimmering (as the atlas's own)
            val shared = decoded.asShared()
            if (shared !== decoded) decoded.recycle()
            shared.setHasMipMap(true)
            return shared
        }
        val bodies = Array(BODIES) { image(bodyName(it)) ?: return null }
        val engravings = HashMap<Tile, Bitmap>()
        for (tile in faces) engravings[tile] = image(engravingName(tile)) ?: return null
        return Images(WIDTH, bodies, engravings).also { images = it }
    }

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "imperial-images").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }

    /** Loads the images off the main thread, if they are not loaded yet. */
    fun loadSoon() {
        if (images == null) worker.execute { load() }
    }
}
