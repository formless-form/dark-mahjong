package com.vortessence.mahjong.engine

import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.repository.ConstellationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SafeOpeningTest {

    private val quad = ConstellationRepository.getById(QUAD)!!

    private fun remove(tiles: List<BoardTile>, a: Int, b: Int) =
        MahjongGameEngine.recalculateFreedom(tiles.map { if (it.id == a || it.id == b) it.copy(isRemoved = true) else it })

    // Deals (of 20) where some first or second match leaves the board unclearable
    private fun losingDeals(layout: Constellation): Int = (0 until 20).count { seed ->
        val deal = BoardSolvabilityGenerator.deal(layout, Random(seed * 7919L + 17))
        val start = MahjongGameEngine.recalculateFreedom(deal.tiles)
        MahjongGameEngine.findAvailablePairs(start).any { (a, b) ->
            val first = remove(start, a, b)
            HintSolver.solve(first, 200_000, hint = deal.plan) == null ||
                MahjongGameEngine.findAvailablePairs(first).any { (c, d) ->
                    HintSolver.solve(remove(first, c, d), 200_000, hint = deal.plan) == null
                }
        }
    }

    @Test
    fun quadsFirstTwoMatchesNeverLose() {
        assertEquals(0, losingDeals(quad))
        // Without it, a quarter of Quad deals had one
        assertTrue(losingDeals(quad.copy(safeMoves = 1)) > 0)
    }

    @Test
    fun onlyQuadChecksTwoMoves() {
        assertEquals(listOf(QUAD), ConstellationRepository.constellations.filter { it.safeMoves > 1 }.map { it.id })
    }

    private companion object {
        const val QUAD = "mah_773823936"
    }
}
