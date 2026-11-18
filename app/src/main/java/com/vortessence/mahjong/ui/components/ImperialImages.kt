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
 * Imperial is drawn from shipped images, only loaded and scaled (Gareth, 2026-11-14: "ship in the
 * app", "in the play build only"):
 *
 *  - [BODIES] stone bodies, outline and sides included, unengraved;
 *  - each face's engraving alone (paint and cut shade), to lay over any body;
 *  - optional smaller surface maps of each: normals (x right, y down, z out of the face, as RGB),
 *    a body's opaque with level sides, an engraving's only where cut. They let the fire relight
 *    the tiles each frame (BoardShadowMap); without them the set keeps its baked light.
 *
 * [WIDTH] px wide (the atlas's largest tile), pre-lit by the lamp like the other sets. Play-build
 * assets only, in [FOLDER]; elsewhere [load] finds nothing and the set is not offered.
 */
internal object ImperialImages {
    const val FOLDER = "imperial"
    const val WIDTH = 256
    const val BODIES = 12
    // Tile width in the surface maps
    const val SURFACE_WIDTH = 128

    /** Set once at start-up (and by tests). */
    @Volatile
    var assets: AssetManager? = null
        set(value) {
            field = value
            availableCache = null
        }

    @Volatile
    private var availableCache: Boolean? = null

    /** [present], cached once the assets are known; false for good if the images fail to load. */
    val available: Boolean
        get() = availableCache ?: present().also { if (assets != null) availableCache = it }

    /** Bodies all one size, engravings body-sized; surface maps likewise at [surfaceWidth], or null. */
    class Images(
        val width: Int, val bodies: Array<Bitmap>, val engravings: Map<Tile, Bitmap>,
        val surfaceWidth: Int, val bodySurfaces: Array<Bitmap>?, val engravingSurfaces: Map<Tile, Bitmap>?,
    )

    /** State, so what is built from the images is rebuilt once they load. */
    val loaded = mutableStateOf(false)

    @Volatile
    private var images: Images? = null

    /** Null until loaded. */
    val ready: Images? get() = images

    /**
     * Whether this build has every image [load] needs (Play does, open-source not), without loading
     * them. One missing would leave the set with nothing to draw.
     */
    fun present(): Boolean {
        val names = try {
            assets?.list(FOLDER)?.toHashSet()
        } catch (e: IOException) {
            null
        } ?: return false
        return (0 until BODIES).all { bodyName(it) in names } && faces.all { engravingName(it) in names }
    }

    fun bodyName(look: Int) = "body-$look.webp"

    fun engravingName(tile: Tile) = "${tile.suit.name.lowercase()}-${tile.value}.webp"

    fun bodySurfaceName(look: Int) = "body-$look-surface.webp"

    fun engravingSurfaceName(tile: Tile) = "${tile.suit.name.lowercase()}-${tile.value}-surface.webp"

    /** Every face once, as in the atlas. */
    val faces: List<Tile> by lazy {
        TileSuit.entries.flatMap { suit -> (1..9).map { Tile(suit, it) } }.filter { TileRenderer.getTileVector(it) != null }
    }

    /** Loads once, on the calling (non-main) thread; null if any is missing. */
    @Synchronized
    fun load(): Images? = images ?: read().also {
        // An image that won't decode: drop the set, so tiles fall back rather than vanish
        if (it == null && assets != null) availableCache = false
    }

    private fun read(): Images? {
        val from = assets ?: return null
        fun decode(name: String): Bitmap? = try {
            from.open("$FOLDER/$name").use { BitmapFactory.decodeStream(it) }
        } catch (e: IOException) {
            null
        }
        fun image(name: String): Bitmap? {
            val decoded = decode(name) ?: return null
            // Mipmapped so it doesn't shimmer when stamped smaller (as the atlas)
            val shared = decoded.asShared()
            if (shared !== decoded) decoded.recycle()
            shared.setHasMipMap(true)
            return shared
        }
        val bodies = Array(BODIES) { image(bodyName(it)) ?: return null }
        val engravings = HashMap<Tile, Bitmap>()
        for (tile in faces) engravings[tile] = image(engravingName(tile)) ?: return null
        // Surface maps: all or none (only ever drawn into the board's own map)
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

    /** Loads off the main thread if not loaded yet. */
    fun loadSoon() {
        if (images == null) worker.execute { load() }
    }
}
