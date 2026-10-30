package com.vortessence.mahjong.data.repository

import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TilePosition
import com.vortessence.mahjong.engine.BoardSolvabilityGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The layouts ported from Mah by ffalt must be complete, distinct and always solvable. */
class MahLayoutsTest {
    private val mah = ConstellationRepository.mahConstellations

    @Test
    fun everyBoardIsAFullSetWithNoOverlappingTiles() {
        assertEquals(29, mah.size)
        assertEquals(mah.size, mah.map { it.id }.toSet().size)
        for (layout in mah) {
            assertEquals(layout.name, 144, layout.positions.size)
            val byLayer = layout.positions.groupBy { it.z }
            for (tiles in byLayer.values) {
                for (i in tiles.indices) for (j in i + 1 until tiles.size) {
                    assertTrue(
                        "${layout.name}: ${tiles[i]} overlaps ${tiles[j]}",
                        !BoardSolvabilityGenerator.isOverlap2D(tiles[i].x, tiles[i].y, tiles[j].x, tiles[j].y),
                    )
                }
            }
        }
    }

    @Test
    fun decodesMahsCompactFormat() {
        // Mah's run [x, count] is count tiles side by side, 2 half-units apart
        val board = MahBoard("t", "T", "Test", com.vortessence.mahjong.data.model.Difficulty.EASY, true, "[[0,[[0,[[2,3],8]],[2,4]]],[1,[[1,[[3,2]]]]]]")
        assertEquals(
            listOf(
                TilePosition(2f, 0f, 0), TilePosition(4f, 0f, 0), TilePosition(6f, 0f, 0), TilePosition(8f, 0f, 0),
                TilePosition(4f, 2f, 0), TilePosition(3f, 1f, 1), TilePosition(5f, 1f, 1),
            ),
            board.positions(),
        )
    }

    // No imported board stacks its tiles like any other layout in the app, wherever it sits
    @Test
    fun noBoardDuplicatesAnotherLayout() {
        fun shape(positions: List<TilePosition>): Set<TilePosition> {
            val minX = positions.minOf { it.x }
            val minY = positions.minOf { it.y }
            return positions.map { TilePosition(it.x - minX, it.y - minY, it.z) }.toSet()
        }
        val all = ConstellationRepository.constellations.map { it.name to shape(it.positions) }
        for (layout in mah) {
            val mine = shape(layout.positions)
            for ((name, other) in all) {
                if (name == layout.name) continue
                val similarity = (mine intersect other).size.toFloat() / (mine union other).size
                assertTrue("${layout.name} is ${(similarity * 100).toInt()}% the same as $name", similarity < 0.8f)
            }
        }
    }

    // Every board takes the complete standard set, each tile once, and always gets a deal that
    // can be cleared (built by lifting free pairs off the full board in reverse)
    @Test
    fun everyBoardDealsTheFullSetSolvably() {
        val fullSet = Tile.createStandard144Set().groupingBy { it }.eachCount()
        for (layout in mah) {
            repeat(3) { seed ->
                val dealt = BoardSolvabilityGenerator.reassignSolvable(layout.positions, Tile.createStandard144Set(), Random(seed))
                assertNotNull("${layout.name} (seed $seed) found no solvable deal", dealt)
                assertEquals(layout.name, fullSet, dealt!!.tiles.values.groupingBy { it }.eachCount())
            }
        }
    }
}
