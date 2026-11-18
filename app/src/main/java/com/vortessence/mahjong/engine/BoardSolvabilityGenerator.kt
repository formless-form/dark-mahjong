package com.vortessence.mahjong.engine

import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.Move
import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TilePosition
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Tiles on positions, and a clearing order: the order the pairs were lifted while dealing. */
class Assignment(val tiles: Map<TilePosition, Tile>, val order: List<Pair<TilePosition, TilePosition>>)

/** A deal and a clearing order ([plan] empty if none is known). */
class Deal(val tiles: List<BoardTile>, val plan: List<Move>)

object BoardSolvabilityGenerator {

    private const val TILE_W = 2.0f
    private const val TILE_H = 2.0f
    private const val EPS = 0.05f

    fun isOverlap2D(ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        val overlapX = max(ax, bx) < min(ax + TILE_W, bx + TILE_W) - EPS
        val overlapY = max(ay, by) < min(ay + TILE_H, by + TILE_H) - EPS
        return overlapX && overlapY
    }

    fun isCovered(a: TilePosition, positions: Collection<TilePosition>): Boolean {
        for (b in positions) {
            if (b.z > a.z && isOverlap2D(a.x, a.y, b.x, b.y)) {
                return true
            }
        }
        return false
    }

    fun isBlockedLeft(a: TilePosition, positions: Collection<TilePosition>): Boolean {
        for (b in positions) {
            if (b.z == a.z && b != a) {
                val touching = abs((b.x + TILE_W) - a.x) < 0.15f || (b.x < a.x && b.x + TILE_W > a.x)
                if (touching) {
                    val verticalOverlap = max(a.y, b.y) < min(a.y + TILE_H, b.y + TILE_H) - EPS
                    if (verticalOverlap) return true
                }
            }
        }
        return false
    }

    fun isBlockedRight(a: TilePosition, positions: Collection<TilePosition>): Boolean {
        for (b in positions) {
            if (b.z == a.z && b != a) {
                val touching = abs((a.x + TILE_W) - b.x) < 0.15f || (b.x > a.x && a.x + TILE_W > b.x)
                if (touching) {
                    val verticalOverlap = max(a.y, b.y) < min(a.y + TILE_H, b.y + TILE_H) - EPS
                    if (verticalOverlap) return true
                }
            }
        }
        return false
    }

    fun isTileFree(a: TilePosition, positions: Collection<TilePosition>): Boolean {
        if (isCovered(a, positions)) return false
        val left = isBlockedLeft(a, positions)
        val right = isBlockedRight(a, positions)
        return !left || !right
    }

    fun generateSolvableBoard(constellation: Constellation, random: Random = Random): List<BoardTile> =
        deal(constellation, random).tiles

    /** A solvable deal, with the order its pairs were lifted in, which hints follow. */
    fun deal(constellation: Constellation, random: Random = Random): Deal {
        val totalCount = constellation.positions.size
        require(totalCount % 2 == 0) { "Constellation tile count must be even" }

        // Scripted layouts (e.g. the tutorial)
        constellation.fixedTiles?.let { fixed ->
            require(fixed.size == totalCount) { "Fixed tiles must match the constellation tile count" }
            return Deal(
                constellation.positions.mapIndexed { index, pos -> BoardTile(id = index, tile = fixed[index], position = pos) },
                plan = emptyList(),
            )
        }

        val pairCount = totalCount / 2

        val tilePairs = generateTilePairs(pairCount, random)

        // Reverse simulation guarantees a solution. Deals with an opening match that loses the board
        // are redealt (a quarter of Quad deals had one)
        var assigned = assignSolvable(constellation.positions, tilePairs, random)
        var tries = 1
        while (assigned != null && tries < SAFE_OPENING_TRIES &&
            !openingIsSafe(constellation.positions, assigned, constellation.safeMoves)
        ) {
            assigned = assignSolvable(constellation.positions, generateTilePairs(pairCount, random), random)
            tries++
        }
        if (assigned != null) {
            val ids = constellation.positions.withIndex().associate { (index, pos) -> pos to index }
            return Deal(
                constellation.positions.mapIndexed { index, pos ->
                    BoardTile(
                        id = index,
                        tile = assigned.tiles.getValue(pos),
                        position = pos,
                        isRemoved = false,
                        isFree = false
                    )
                },
                plan = assigned.order.map { (a, b) -> Move(ids.getValue(a), ids.getValue(b)) },
            )
        }

        // Unusually constrained layout: direct pairing
        return Deal(fallbackAssignment(constellation.positions, tilePairs, random), plan = emptyList())
    }

    /**
     * Whether the first [moves] matches, whichever are made, leave a clearable board. With one move
     * it's checked only when at most [NARROW_OPENING] are open. The deal's order guides the search;
     * running out of [budget] counts as lost.
     */
    internal fun openingIsSafe(
        positions: List<TilePosition>,
        assignment: Assignment,
        moves: Int = 1,
        budget: Int = OPENING_BUDGET,
    ): Boolean {
        val ids = positions.withIndex().associate { (index, pos) -> pos to index }
        val tiles = MahjongGameEngine.recalculateFreedom(
            positions.mapIndexed { index, pos -> BoardTile(id = index, tile = assignment.tiles.getValue(pos), position = pos) },
        )
        val plan = assignment.order.map { (a, b) -> Move(ids.getValue(a), ids.getValue(b)) }
        // With many open, a poor first choice is rarely fatal; the search is kept for narrow ones
        if (moves <= 1 && MahjongGameEngine.findAvailablePairs(tiles).size > NARROW_OPENING) return true
        return noMatchLoses(tiles, plan, moves, budget)
    }

    private fun noMatchLoses(tiles: List<BoardTile>, plan: List<Move>, moves: Int, budget: Int): Boolean {
        for ((a, b) in MahjongGameEngine.findAvailablePairs(tiles)) {
            val after = MahjongGameEngine.recalculateFreedom(tiles.map { if (it.id == a || it.id == b) it.copy(isRemoved = true) else it })
            if (after.all { it.isRemoved }) continue
            if (HintSolver.solve(after, budget, hint = plan) == null) return false
            if (moves > 1 && !noMatchLoses(after, plan, moves - 1, budget)) return false
        }
        return true
    }

    // Deals tried for a safe opening; OPENING_BUDGET is the search per opening
    private const val SAFE_OPENING_TRIES = 12
    private const val NARROW_OPENING = 5
    private const val OPENING_BUDGET = 20_000

    /** Redistributes [tiles] solvably; null if they can't be paired or no arrangement was found. */
    fun reassignSolvable(
        positions: List<TilePosition>,
        tiles: List<Tile>,
        random: Random = Random,
    ): Assignment? {
        val pairs = pairUp(tiles) ?: return null
        return assignSolvable(positions, pairs.shuffled(random), random)
    }

    private fun assignSolvable(
        positions: List<TilePosition>,
        pairs: List<Pair<Tile, Tile>>,
        random: Random,
    ): Assignment? {
        for (@Suppress("UNUSED_VARIABLE") attempt in 0 until 50) {
            val assigned = tryReverseSimulation(positions, pairs, random)
            if (assigned != null) {
                return assigned
            }
        }
        return null
    }

    /** Null if some tile has no partner. */
    private fun pairUp(tiles: List<Tile>): List<Pair<Tile, Tile>>? {
        val available = tiles.toMutableList()
        val pairs = mutableListOf<Pair<Tile, Tile>>()
        while (available.isNotEmpty()) {
            val t1 = available.removeAt(0)
            val matchIdx = available.indexOfFirst { t1.matches(it) }
            if (matchIdx == -1) return null
            pairs.add(Pair(t1, available.removeAt(matchIdx)))
        }
        return pairs
    }

    private fun tryReverseSimulation(
        allPositions: List<TilePosition>,
        pairs: List<Pair<Tile, Tile>>,
        random: Random,
    ): Assignment? {
        val remaining = allPositions.toMutableList()
        val assignedTiles = mutableMapOf<TilePosition, Tile>()
        val order = mutableListOf<Pair<TilePosition, TilePosition>>()

        // From the full board, put each pair on two free positions and lift them: forward play can
        // always take them in this order
        for (pair in pairs) {
            val free = remaining.filter { isTileFree(it, remaining) }
            if (free.size < 2) {
                return null
            }
            val shuffled = free.shuffled(random)
            val pos1 = shuffled[0]
            val pos2 = shuffled[1]

            assignedTiles[pos1] = pair.first
            assignedTiles[pos2] = pair.second
            order.add(pos1 to pos2)

            remaining.remove(pos1)
            remaining.remove(pos2)
        }

        return Assignment(assignedTiles, order)
    }

    private fun fallbackAssignment(
        allPositions: List<TilePosition>,
        pairs: List<Pair<Tile, Tile>>,
        random: Random
    ): List<BoardTile> {
        val flatTiles = mutableListOf<Tile>()
        for (p in pairs) {
            flatTiles.add(p.first)
            flatTiles.add(p.second)
        }
        flatTiles.shuffle(random)

        return allPositions.mapIndexed { index, pos ->
            BoardTile(
                id = index,
                tile = flatTiles[index % flatTiles.size],
                position = pos,
                isRemoved = false,
                isFree = false
            )
        }
    }

    fun generateTilePairs(pairCount: Int, random: Random = Random): List<Pair<Tile, Tile>> {
        val full144 = Tile.createStandard144Set().shuffled(random)
        val pairs = mutableListOf<Pair<Tile, Tile>>()

        val available = full144.toMutableList()
        var i = 0
        while (i < available.size && pairs.size < pairCount) {
            val t1 = available[i]
            var matchIdx = -1
            for (j in (i + 1) until available.size) {
                if (t1.matches(available[j])) {
                    matchIdx = j
                    break
                }
            }
            if (matchIdx != -1) {
                val t2 = available.removeAt(matchIdx)
                available.removeAt(i)
                pairs.add(Pair(t1, t2))
            } else {
                i++
            }
        }

        // More pairs than the standard 144 holds: duplicate some
        while (pairs.size < pairCount) {
            val randomPair = pairs.random(random)
            pairs.add(Pair(randomPair.first.copy(), randomPair.second.copy()))
        }

        return pairs.shuffled(random)
    }
}
