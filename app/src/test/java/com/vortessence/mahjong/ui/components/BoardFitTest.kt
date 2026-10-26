package com.vortessence.mahjong.ui.components

import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every layout, fitted to the board area and projected through the tilted camera, must keep
 * every corner of every tile (thickness included) on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BoardFitTest {
    // The Pixel 9 Pro's board area (between the header and the toolbar), and a squatter and a
    // taller screen for good measure
    private val canvases = listOf(1280f to 2240f, 1080f to 1500f, 1080f to 2400f)
    private val padding = 48f

    @Test
    fun everyLayoutFitsOnScreen() {
        val layouts = (ConstellationRepository.constellations + ConstellationRepository.getLevelConstellations(false))
            .distinctBy { it.id }
        for ((w, h) in canvases) {
            val camera = TableCamera.planeMatrix(w, h)
            for (layout in layouts) {
                val tiles = MahjongGameEngine.startNewGame(layout).tiles
                val t = computeBoardTransform(tiles, w, h, padding)
                for (tile in tiles) {
                    val x = t.tilePixelX(tile.x, tile.z)
                    val y = t.tilePixelY(tile.y, tile.z)
                    val corners = floatArrayOf(
                        x, y,
                        x + t.tileWidth + t.depthX, y,
                        x + t.tileWidth + t.depthX, y + t.tileHeight + t.depthY,
                        x, y + t.tileHeight + t.depthY,
                    )
                    camera.mapPoints(corners)
                    for (i in 0 until 4) {
                        val cx = corners[i * 2]
                        val cy = corners[i * 2 + 1]
                        assertTrue(
                            "${layout.name} on ${w.toInt()}x${h.toInt()}: tile ${tile.id} reaches ($cx, $cy)",
                            cx in 0f..w && cy in 0f..h,
                        )
                    }
                }
            }
        }
    }
}
