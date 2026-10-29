package com.vortessence.mahjong.ui.components

import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Culling leaves out only tiles nothing of which can show, and keeps everything else. */
class TileCullingTest {
    // Pixels of each tile left showing, painting whole rectangles (face and walls) in draw order
    private fun showing(tiles: List<BoardTile>): Map<Int, Int> {
        val unit = 24f
        val t = BoardTransform(unit, 2f * unit, 2f * unit * BoardTransform.ASPECT_RATIO, 300f, 300f,
            2f * unit * BoardTransform.LAYER_SHIFT_FACTOR_X, 2f * unit * BoardTransform.ASPECT_RATIO * BoardTransform.LAYER_SHIFT_FACTOR_Y,
            2f * unit * BoardTransform.LAYER_SHIFT_FACTOR_X, 2f * unit * BoardTransform.ASPECT_RATIO * BoardTransform.LAYER_SHIFT_FACTOR_Y)
        val w = 2600
        val h = 3000
        val owner = IntArray(w * h) { -1 }
        val sorted = tiles.filter { !it.isRemoved }.sortedWith(TileDrawOrder)
        for (tile in sorted) {
            val x0 = t.tilePixelX(tile.x, tile.z).toInt()
            val y0 = t.tilePixelY(tile.y, tile.z).toInt()
            val x1 = (t.tilePixelX(tile.x, tile.z) + t.tileWidth + t.depthX).toInt()
            val y1 = (t.tilePixelY(tile.y, tile.z) + t.tileHeight + t.depthY).toInt()
            for (y in y0 until y1) for (x in x0 until x1) owner[y * w + x] = tile.id
        }
        return owner.filter { it >= 0 }.groupingBy { it }.eachCount()
    }

    @Test
    fun onlyCoveredTilesAreLeftOut() {
        val layouts = (ConstellationRepository.constellations + ConstellationRepository.getLevelConstellations(false)).distinctBy { it.id }
        for (layout in layouts) {
            val tiles = MahjongGameEngine.startNewGame(layout).tiles
            val kept = TileCulling.visibleInDrawOrder(tiles).map { it.id }.toSet()
            val seen = showing(tiles)
            for (tile in tiles) {
                // Anything showing even as rectangles is kept
                if ((seen[tile.id] ?: 0) > 0) assertTrue("${layout.name}: tile ${tile.id} shows but was culled", tile.id in kept)
            }
        }
    }

    @Test
    fun quadDrawsFarFewerTiles() {
        val quad = ConstellationRepository.constellations.first { it.name == "Quad" }
        val tiles = MahjongGameEngine.startNewGame(quad).tiles
        val kept = TileCulling.visibleInDrawOrder(tiles).size
        // 44 of Quad's 144 are hidden as plain rectangles; rounded corners and the slight
        // off-square placement let a few peek out
        assertTrue("Quad keeps $kept", kept in 100..115)
    }

    @Test
    fun drawOrderIsKept() {
        val quad = ConstellationRepository.constellations.first { it.name == "Quad" }
        val tiles = MahjongGameEngine.startNewGame(quad).tiles
        val kept = TileCulling.visibleInDrawOrder(tiles)
        assertEquals(kept.sortedWith(TileDrawOrder), kept)
    }
}
