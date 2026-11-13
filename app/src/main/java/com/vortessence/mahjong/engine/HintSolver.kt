package com.vortessence.mahjong.engine

import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.Move
import com.vortessence.mahjong.data.model.TilePosition
import java.util.BitSet

/**
 * Finds a way to clear the tiles left on a board, for hints when no plan from the deal still
 * holds (a restored game, or a player who paired tiles differently): a depth-first search over
 * free matching pairs, the most promising first, that takes moves that can't be wrong without
 * trying alternatives, remembers dead ends, and gives up after [budget] positions.
 */
object HintSolver {

    /**
     * The pairs to take, in order, to clear [tiles]; null if none was found within [budget].
     * [hint] is a plan that no longer holds but still knows the board: tiles it takes early are
     * tried first.
     */
    fun solve(tiles: List<BoardTile>, budget: Int = 20_000, hint: List<Move> = emptyList()): List<Move>? {
        val activeAt = tiles.indices.filter { !tiles[it].isRemoved }
        val active = activeAt.map { tiles[it] }
        val n = active.size
        if (n == 0) return emptyList()
        val board = Board(active, activeAt, Relations.of(tiles), turns(hint))

        val present = BitSet(n).apply { set(0, n) }
        val deadEnds = HashSet<BitSet>()
        // The tiles taken so far, two by two
        val path = IntArray(n)
        var depth = 0
        var explored = 0

        // true: cleared; false: a dead end; null: out of budget
        fun search(): Boolean? {
            val mark = depth
            depth = board.takeSafe(present, path, depth)
            fun undoSafe() {
                for (k in mark until depth) present.set(path[k])
                depth = mark
            }
            if (present.isEmpty) return true
            if (present in deadEnds) {
                undoSafe()
                return false
            }
            if (++explored > budget) return null
            for (pair in board.rankedPairs(present)) {
                val i = Board.first(pair)
                val j = Board.second(pair)
                present.clear(i)
                present.clear(j)
                path[depth++] = i
                path[depth++] = j
                val result = search()
                if (result != false) return result
                depth -= 2
                present.set(i)
                present.set(j)
            }
            deadEnds.add(present.clone() as BitSet)
            undoSafe()
            return false
        }

        if (search() != true) return null
        return (0 until depth step 2).map { Move(active[path[it]].id, active[path[it + 1]].id) }
    }

    /** The free pairs of [tiles], the most promising first: the best guess when no plan is found. */
    fun rank(tiles: List<BoardTile>): List<Pair<Int, Int>> {
        val activeAt = tiles.indices.filter { !tiles[it].isRemoved }
        val active = activeAt.map { tiles[it] }
        val present = BitSet(active.size).apply { set(0, active.size) }
        return Board(active, activeAt, Relations.of(tiles), emptyMap()).rankedPairs(present)
            .map { active[Board.first(it)].id to active[Board.second(it)].id }
    }

    // Each tile's turn in [plan]
    private fun turns(plan: List<Move>): Map<Int, Int> {
        val turns = HashMap<Int, Int>()
        plan.forEachIndexed { turn, move ->
            turns[move.tileId1] = turn
            turns[move.tileId2] = turn
        }
        return turns
    }

    /**
     * Which tiles of a whole layout lie on each tile, and flank it on the left and on the right
     * (by index in its tile list): the same however many tiles are left, so worked out once per
     * layout rather than for every search. A deal checks each of its openings with a search of
     * the same layout (BoardSolvabilityGenerator.openingIsSafe), and each would otherwise scan
     * every pair of tiles again.
     */
    private class Relations(val positions: List<TilePosition>) {
        val above: Array<IntArray>
        val left: Array<IntArray>
        val right: Array<IntArray>

        init {
            val n = positions.size
            val a = Array(n) { ArrayList<Int>() }
            val l = Array(n) { ArrayList<Int>() }
            val r = Array(n) { ArrayList<Int>() }
            for (i in 0 until n) {
                for (j in 0 until n) {
                    if (i == j) continue
                    val p = positions[i]
                    val q = listOf(positions[j])
                    if (BoardSolvabilityGenerator.isCovered(p, q)) a[i].add(j)
                    if (BoardSolvabilityGenerator.isBlockedLeft(p, q)) l[i].add(j)
                    if (BoardSolvabilityGenerator.isBlockedRight(p, q)) r[i].add(j)
                }
            }
            above = Array(n) { a[it].toIntArray() }
            left = Array(n) { l[it].toIntArray() }
            right = Array(n) { r[it].toIntArray() }
        }

        companion object {
            // The last layout's, kept for the next search (hints and deals search from more than
            // one thread, each reading or replacing the whole entry)
            @Volatile
            private var last: Relations? = null

            fun of(tiles: List<BoardTile>): Relations {
                val positions = tiles.map { it.position }
                last?.takeIf { it.positions == positions }?.let { return it }
                return Relations(positions).also { last = it }
            }
        }
    }

    private class Board(active: List<BoardTile>, activeAt: List<Int>, relations: Relations, turns: Map<Int, Int>) {
        private val n = active.size

        // Each tile's turn in an old plan (later than all for tiles it doesn't have)
        private val turn = IntArray(n) { (turns[active[it].id] ?: MAX_TURN).coerceAtMost(MAX_TURN) }

        // For each tile, the tiles lying on it, and those beside it on the left and on the right
        private val above = Array(n) { IntArray(0) }
        private val left = Array(n) { IntArray(0) }
        private val right = Array(n) { IntArray(0) }

        // How many tiles each tile holds down (lies on, or flanks): taking it frees the most
        private val holds = IntArray(n)
        private val z = IntArray(n) { active[it].position.z }

        // Tiles that match each other share a kind (any two Flowers, any two Seasons, or the same
        // face), numbered from 0
        private val kind = IntArray(n)
        private val kinds: Int

        // Scratch space, reused: a search finishes with one position before moving to the next
        private val free = IntArray(n)
        private val presentOfKind: IntArray
        private val freeOfKind: IntArray

        init {
            // The layout's relations, kept to the tiles left and renumbered as they are here
            val here = IntArray(relations.positions.size) { -1 }
            activeAt.forEachIndexed { i, at -> here[at] = i }
            fun mapped(of: IntArray) = of.map { here[it] }.filter { it >= 0 }.toIntArray()
            for (i in 0 until n) {
                above[i] = mapped(relations.above[activeAt[i]])
                left[i] = mapped(relations.left[activeAt[i]])
                right[i] = mapped(relations.right[activeAt[i]])
                for (j in above[i]) holds[j] += 2
                for (j in left[i]) holds[j]++
                for (j in right[i]) holds[j]++
            }
            val firsts = ArrayList<Int>()
            for (i in 0 until n) {
                val k = firsts.indexOfFirst { active[it].tile.matches(active[i].tile) }
                kind[i] = if (k >= 0) k else firsts.size.also { firsts.add(i) }
            }
            kinds = firsts.size
            presentOfKind = IntArray(kinds)
            freeOfKind = IntArray(kinds)
        }

        private fun noneIn(present: BitSet, of: IntArray): Boolean {
            for (t in of) if (present.get(t)) return false
            return true
        }

        private fun isFree(i: Int, present: BitSet) =
            noneIn(present, above[i]) && (noneIn(present, left[i]) || noneIn(present, right[i]))

        // Fills [free] with the free tiles of [present], and counts them and all tiles by kind
        private fun survey(present: BitSet): Int {
            presentOfKind.fill(0)
            freeOfKind.fill(0)
            var count = 0
            var i = present.nextSetBit(0)
            while (i >= 0) {
                presentOfKind[kind[i]]++
                if (isFree(i, present)) {
                    free[count++] = i
                    freeOfKind[kind[i]]++
                }
                i = present.nextSetBit(i + 1)
            }
            return count
        }

        /**
         * Takes from [present] every kind whose tiles left are all free: they can only pair with
         * each other, and taking tiles never blocks one, so it can't be a wrong move. Repeats until
         * none is left, writing the tiles taken into [path] from [depth]; returns the new depth.
         */
        fun takeSafe(present: BitSet, path: IntArray, depth: Int): Int {
            var d = depth
            while (true) {
                val count = survey(present)
                val before = d
                for (f in 0 until count) {
                    val k = kind[free[f]]
                    if (presentOfKind[k] != freeOfKind[k] || freeOfKind[k] % 2 != 0) continue
                    // The whole kind at once, so the path pairs its tiles with each other
                    for (g in f until count) {
                        val t = free[g]
                        if (kind[t] != k) continue
                        present.clear(t)
                        path[d++] = t
                    }
                    freeOfKind[k] = -1
                }
                if (d == before) return d
            }
        }

        /**
         * The free matching pairs, the most promising first: those the old plan takes soonest, then
         * higher tiles, then those holding down most.
         */
        fun rankedPairs(present: BitSet): List<Long> {
            val count = survey(present)
            val pairs = ArrayList<Long>()
            for (x in 0 until count) {
                for (y in x + 1 until count) {
                    val i = free[x]
                    val j = free[y]
                    if (kind[i] != kind[j]) continue
                    val soon = (MAX_TURN - maxOf(turn[i], turn[j])).toLong()
                    val shape = ((z[i] + z[j]) * 1000L + holds[i] + holds[j]).coerceAtMost(0xFFFFF)
                    pairs.add((((soon shl 20) or shape) shl 32) or (i.toLong() shl 16) or j.toLong())
                }
            }
            pairs.sortDescending()
            return pairs
        }

        companion object {
            const val MAX_TURN = 1023

            fun first(pair: Long) = ((pair shr 16) and 0xFFFF).toInt()
            fun second(pair: Long) = (pair and 0xFFFF).toInt()
        }
    }
}
