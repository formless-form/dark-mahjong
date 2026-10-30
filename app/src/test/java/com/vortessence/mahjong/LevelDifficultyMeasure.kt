package com.vortessence.mahjong

import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.BoardSolvabilityGenerator
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.stream.IntStream
import kotlin.random.Random

/**
 * Measures how hard each level really is: deals of every layout, played to the end without hints,
 * undo or reshuffles, by two simple players. One takes any free pair at random (a beginner); the
 * other takes the highest pair, then the one holding down the most tiles (a careful player).
 * The share of deals each clears is written to build/level-difficulty.csv.
 *
 * Slow, so it only runs when asked: MEASURE_LEVELS=300 (deals per level) in the environment.
 */
class LevelDifficultyMeasure {

    @Test
    fun measure() {
        val deals = System.getenv("MEASURE_LEVELS")?.toIntOrNull()
        assumeTrue("Set MEASURE_LEVELS to run", deals != null)
        // Every layout (the Layouts list is ordered the same way), its level number if it's a level
        val layouts = ConstellationRepository.constellations.filter { it.id != ConstellationRepository.TUTORIAL_ID }
        val rows = layouts.map { layout ->
            val level = ConstellationRepository.levelIndexOf(layout.id)
            val (random, careful) = clearRates(layout, deals!!)
            println("${layout.name}: random ${pct(random)}, careful ${pct(careful)}")
            listOf(
                level ?: "", layout.id, layout.name, layout.tileCount, layout.difficulty.name,
                "%.4f".format(random), "%.4f".format(careful),
            ).joinToString(",")
        }
        File("build/level-difficulty.csv").writeText(
            "level,id,name,tiles,label,random,careful\n" + rows.joinToString("\n") + "\n",
        )
    }

    private fun pct(x: Double) = "%.1f%%".format(x * 100)

    private fun clearRates(layout: Constellation, deals: Int): Pair<Double, Double> {
        val board = Board(layout)
        val results = IntStream.range(0, deals).parallel().mapToObj { seed ->
            val tiles = BoardSolvabilityGenerator.generateSolvableBoard(layout, Random(seed * 7919L + 17))
            val faces = IntArray(tiles.size)
            tiles.sortedBy { it.id }.forEachIndexed { i, t -> faces[i] = kindOf(t) }
            val play = Random(seed.toLong())
            Pair(board.play(faces, play, careful = false), board.play(faces, play, careful = true))
        }.toList()
        return results.count { it.first } / deals.toDouble() to results.count { it.second } / deals.toDouble()
    }

    // Tiles that match share a kind: any two Flowers, any two Seasons, otherwise the same face
    private fun kindOf(t: BoardTile): Int = when (t.tile.suit.name) {
        "FLOWER" -> 1000
        "SEASON" -> 1001
        else -> t.tile.suit.ordinal * 100 + t.tile.value
    }

    private class Board(layout: Constellation) {
        private val positions = layout.positions
        private val n = positions.size
        private val above = Array(n) { IntArray(0) }
        private val left = Array(n) { IntArray(0) }
        private val right = Array(n) { IntArray(0) }
        private val holds = IntArray(n)
        private val z = IntArray(n) { positions[it].z }

        init {
            for (i in 0 until n) {
                val a = ArrayList<Int>()
                val l = ArrayList<Int>()
                val r = ArrayList<Int>()
                for (j in 0 until n) {
                    if (i == j) continue
                    val q = listOf(positions[j])
                    if (BoardSolvabilityGenerator.isCovered(positions[i], q)) { a.add(j); holds[j] += 2 }
                    if (BoardSolvabilityGenerator.isBlockedLeft(positions[i], q)) { l.add(j); holds[j]++ }
                    if (BoardSolvabilityGenerator.isBlockedRight(positions[i], q)) { r.add(j); holds[j]++ }
                }
                above[i] = a.toIntArray()
                left[i] = l.toIntArray()
                right[i] = r.toIntArray()
            }
        }

        private fun none(present: BooleanArray, of: IntArray): Boolean {
            for (t in of) if (present[t]) return false
            return true
        }

        // True if the board was cleared
        fun play(faces: IntArray, random: Random, careful: Boolean): Boolean {
            val present = BooleanArray(n) { true }
            var left = n
            val free = IntArray(n)
            while (left > 0) {
                var count = 0
                for (i in 0 until n) {
                    if (present[i] && none(present, above[i]) && (none(present, this.left[i]) || none(present, right[i]))) {
                        free[count++] = i
                    }
                }
                var bestA = -1
                var bestB = -1
                var bestScore = Long.MIN_VALUE
                var seen = 0
                for (x in 0 until count) {
                    for (y in x + 1 until count) {
                        val a = free[x]
                        val b = free[y]
                        if (faces[a] != faces[b]) continue
                        if (careful) {
                            val score = ((z[a] + z[b]) * 1000L + holds[a] + holds[b]) * 64 + random.nextInt(64)
                            if (score > bestScore) { bestScore = score; bestA = a; bestB = b }
                        } else {
                            // Reservoir sampling: each pair equally likely
                            seen++
                            if (random.nextInt(seen) == 0) { bestA = a; bestB = b }
                        }
                    }
                }
                if (bestA < 0) return false
                present[bestA] = false
                present[bestB] = false
                left -= 2
            }
            return true
        }
    }
}
