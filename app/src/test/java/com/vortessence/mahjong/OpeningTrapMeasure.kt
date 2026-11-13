package com.vortessence.mahjong

import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.BoardSolvabilityGenerator
import com.vortessence.mahjong.engine.HintSolver
import com.vortessence.mahjong.engine.MahjongGameEngine
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/**
 * How often deals open with a match that leaves the board unclearable (TRAP_IDS=id,id with
 * TRAP_DEALS deals each), and how long every layout takes to deal (TRAP_ALL=1). Slow, so only
 * when asked. Quad: 10 of 40 deals had one before openings were checked (2026-11-13), none after.
 */
class OpeningTrapMeasure {
    @Test
    fun dealTimes() {
        assumeTrue(System.getenv("TRAP_ALL") != null)
        var worst = 0.0
        // Warm the JIT first, so the times are the dealing's own
        repeat(3) { BoardSolvabilityGenerator.deal(ConstellationRepository.getById("mah_773823936")!!, Random(it.toLong())) }
        for (layout in ConstellationRepository.constellations.filter { it.fixedTiles == null }) {
            val t0 = System.nanoTime()
            repeat(5) { BoardSolvabilityGenerator.deal(layout, Random(it * 13L + 1)) }
            val ms = (System.nanoTime() - t0) / 1e6 / 5
            if (ms > 40) println("TRAP slow ${layout.name}: ${"%.0f".format(ms)} ms")
            worst = maxOf(worst, ms)
        }
        println("TRAP worst deal ${"%.0f".format(worst)} ms over ${ConstellationRepository.constellations.size} layouts")
    }

    @Test
    fun measure() {
        val ids = System.getenv("TRAP_IDS")?.split(",") ?: emptyList()
        assumeTrue(ids.isNotEmpty())
        val deals = System.getenv("TRAP_DEALS")?.toIntOrNull() ?: 40
        for (id in ids) {
            val layout = ConstellationRepository.getById(id)!!
            var trapped = 0; var unknown = 0; var openings = 0; var trapMoves = 0
            for (seed in 0 until deals) {
                val deal = BoardSolvabilityGenerator.deal(layout, Random(seed * 31L + 5))
                val tiles = MahjongGameEngine.recalculateFreedom(deal.tiles)
                val pairs = MahjongGameEngine.findAvailablePairs(tiles)
                openings += pairs.size
                var traps = 0; var unk = 0
                for ((a, b) in pairs) {
                    val after = MahjongGameEngine.recalculateFreedom(tiles.map { if (it.id == a || it.id == b) it.copy(isRemoved = true) else it })
                    val plan = HintSolver.solve(after, budget = 400_000, hint = deal.plan)
                    if (plan == null) traps++
                }
                if (traps > 0) trapped++
                trapMoves += traps
            }
            val t0 = System.nanoTime()
            repeat(deals) { BoardSolvabilityGenerator.deal(layout, Random(it * 13L + 1)) }
            val ms = (System.nanoTime() - t0) / 1e6 / deals
            println("TRAP deal time ${"%.0f".format(ms)} ms avg")
            println("TRAP $id (${layout.name}): $trapped/$deals deals have a losing first move; ${trapMoves}/$openings first moves lose; free pairs at start avg ${"%.1f".format(openings.toDouble() / deals)}")
        }
    }
}
