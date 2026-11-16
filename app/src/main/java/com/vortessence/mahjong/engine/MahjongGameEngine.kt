package com.vortessence.mahjong.engine

import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.Move
import kotlin.random.Random

sealed class TileTapResult {
    data class Blocked(val tileId: Int) : TileTapResult()
    data class Selected(val tileId: Int) : TileTapResult()
    data object Deselected : TileTapResult()
    data class Matched(val tileId1: Int, val tileId2: Int, val isWon: Boolean, val isStalemate: Boolean) : TileTapResult()
    data class SelectionChanged(val newSelectedTileId: Int) : TileTapResult()
}

object MahjongGameEngine {


    fun startNewGame(constellation: Constellation, random: Random = Random): GameState {
        val deal = BoardSolvabilityGenerator.deal(constellation, random)
        return stateOf(constellation, deal.tiles, plan = deal.plan)
            .copy(dealt = deal.tiles.sortedBy { it.id }.map { it.tile }, dealtPlan = deal.plan)
    }

    /**
     * The same deal from the start, with the dealt faces (a reshuffle changes them). Older saves
     * without the deal keep their current faces.
     */
    fun replay(state: GameState): GameState {
        val faces = state.dealt.takeIf { it.size == state.tiles.size }
        val tiles = state.tiles.map { t -> t.copy(isRemoved = false, tile = faces?.getOrNull(t.id) ?: t.tile) }
        return stateOf(state.constellation, tiles, plan = if (faces != null) state.dealtPlan else emptyList())
            .copy(dealt = state.dealt, dealtPlan = state.dealtPlan)
    }

    /** Nothing selected: a new deal, or one restored from a save. */
    fun stateOf(
        constellation: Constellation,
        tiles: List<BoardTile>,
        moveHistory: List<Move> = emptyList(),
        plan: List<Move> = emptyList(),
    ): GameState {
        val tilesWithFreedom = recalculateFreedom(tiles)
        val availablePairs = findAvailablePairs(tilesWithFreedom)
        return GameState(
            constellation = constellation,
            tiles = tilesWithFreedom,
            moveHistory = moveHistory,
            isWon = tilesWithFreedom.none { !it.isRemoved },
            isStalemate = isStuck(tilesWithFreedom, availablePairs),
            availablePairsCount = availablePairs.size,
            plan = plan,
        )
    }

    fun handleTileTap(currentState: GameState, tileId: Int): Pair<GameState, TileTapResult> {
        val targetTile = currentState.tiles.firstOrNull { it.id == tileId }
            ?: return currentState to TileTapResult.Deselected

        if (targetTile.isRemoved) {
            return currentState to TileTapResult.Deselected
        }

        if (!targetTile.isFree) {
            val updatedState = currentState.copy(
                blockedTileId = tileId
            )
            return updatedState to TileTapResult.Blocked(tileId)
        }

        val currentSelectedId = currentState.selectedTileId

        // The hint stays lit while one of its tiles is picked, so its partner is easy to find
        val hintKept = currentState.hintPair?.takeIf { tileId == it.first || tileId == it.second }

        if (currentSelectedId == null) {
            val updatedState = currentState.copy(
                selectedTileId = tileId,
                hintPair = hintKept,
                blockedTileId = null
            )
            return updatedState to TileTapResult.Selected(tileId)
        }

        if (currentSelectedId == tileId) {
            val updatedState = currentState.copy(
                selectedTileId = null,
                hintPair = hintKept,
                blockedTileId = null
            )
            return updatedState to TileTapResult.Deselected
        }

        val selectedTile = currentState.tiles.firstOrNull { it.id == currentSelectedId }
        if (selectedTile != null && (selectedTile.tile.matches(targetTile.tile))) {
            val updatedTiles = currentState.tiles.map { t ->
                if (t.id == currentSelectedId || t.id == tileId) {
                    t.copy(isRemoved = true, isFree = false)
                } else {
                    t
                }
            }

            val refreshedTiles = recalculateFreedom(updatedTiles)
            val availablePairs = findAvailablePairs(refreshedTiles)
            val remainingCount = refreshedTiles.count { !it.isRemoved }
            val isWon = remainingCount == 0
            val isStalemate = isStuck(refreshedTiles, availablePairs)

            val newHistory = currentState.moveHistory + Move(currentSelectedId, tileId)

            val updatedState = currentState.copy(
                tiles = refreshedTiles,
                selectedTileId = null,
                hintPair = null,
                blockedTileId = null,
                moveHistory = newHistory,
                isWon = isWon,
                isStalemate = isStalemate,
                availablePairsCount = availablePairs.size
            )

            return updatedState to TileTapResult.Matched(currentSelectedId, tileId, isWon, isStalemate)
        }

        val updatedState = currentState.copy(
            selectedTileId = tileId,
            hintPair = hintKept,
            blockedTileId = null
        )
        return updatedState to TileTapResult.SelectionChanged(tileId)
    }

    fun undo(currentState: GameState): GameState {
        if (currentState.moveHistory.isEmpty()) return currentState

        val lastMove = currentState.moveHistory.last()
        val remainingHistory = currentState.moveHistory.dropLast(1)

        val updatedTiles = currentState.tiles.map { t ->
            if (t.id == lastMove.tileId1 || t.id == lastMove.tileId2) {
                t.copy(isRemoved = false)
            } else {
                t
            }
        }

        val refreshedTiles = recalculateFreedom(updatedTiles)
        val availablePairs = findAvailablePairs(refreshedTiles)

        return currentState.copy(
            tiles = refreshedTiles,
            selectedTileId = null,
            hintPair = null,
            blockedTileId = null,
            moveHistory = remainingHistory,
            isWon = false,
            isStalemate = isStuck(refreshedTiles, availablePairs),
            availablePairsCount = availablePairs.size
        )
    }

    /** [plan] is empty if none was found. */
    data class Hint(val pair: Pair<Int, Int>, val plan: List<Move>)

    /**
     * A free pair from a plan that still holds (the deal's, or the solver's), else the most
     * promising free pair; null if none is free. Can be slow on big boards: background thread only.
     */
    fun findHint(state: GameState): Hint? {
        if (findAvailablePairs(state.tiles).isEmpty()) return null
        val plan = state.plan.takeIf { planHolds(state.tiles, it) } ?: HintSolver.solve(state.tiles, hint = state.plan)
        val candidates = if (plan != null) {
            val free = state.tiles.filter { it.isFree }.map { it.id }.toSet()
            plan.filter { it.tileId1 in free && it.tileId2 in free }.map { it.tileId1 to it.tileId2 }
        } else {
            HintSolver.rank(state.tiles)
        }
        if (candidates.isEmpty()) return null
        return Hint(candidates.first(), plan ?: emptyList())
    }

    /**
     * Every tile left is paired with its planned, still matching partner. Taking pairs early never
     * blocks a tile, so any free planned pair can go now.
     */
    fun planHolds(tiles: List<BoardTile>, plan: List<Move>): Boolean {
        if (plan.isEmpty()) return false
        val byId = tiles.associateBy { it.id }
        val covered = HashSet<Int>()
        for (move in plan) {
            val a = byId[move.tileId1] ?: return false
            val b = byId[move.tileId2] ?: return false
            if (a.isRemoved != b.isRemoved) return false
            if (a.isRemoved) continue
            if (!a.tile.matches(b.tile)) return false
            covered.add(a.id)
            covered.add(b.id)
        }
        return tiles.all { it.isRemoved || it.id in covered }
    }

    fun reshuffleRemaining(currentState: GameState): GameState {
        // Some leftovers have no solvable arrangement (e.g. the last two stacked), so take back
        // pairs until one exists
        var state = currentState
        var solvable: Assignment?
        while (true) {
            val activeTiles = state.tiles.filter { !it.isRemoved }
            if (activeTiles.isEmpty()) return currentState
            solvable = BoardSolvabilityGenerator.reassignSolvable(
                positions = activeTiles.map { it.position },
                tiles = activeTiles.map { it.tile },
            )
            if (solvable != null || state.moveHistory.isEmpty()) break
            state = undo(state)
        }

        // No solvable one: plain shuffle
        val activeTiles = state.tiles.filter { !it.isRemoved }
        val solvableMap = solvable
        val activeMap = if (solvableMap != null) {
            activeTiles.associate { it.id to solvableMap.tiles.getValue(it.position) }
        } else {
            val activeTilesValues = activeTiles.map { it.tile }.shuffled()
            activeTiles.indices.associate { i ->
                activeTiles[i].id to activeTilesValues[i]
            }
        }

        val updatedTiles = state.tiles.map { t ->
            if (activeMap.containsKey(t.id)) {
                t.copy(tile = activeMap[t.id]!!)
            } else {
                t
            }
        }

        val refreshedTiles = recalculateFreedom(updatedTiles)
        val availablePairs = findAvailablePairs(refreshedTiles)

        val ids = activeTiles.associate { it.position to it.id }
        val plan = solvableMap?.order?.map { (p, q) -> Move(ids.getValue(p), ids.getValue(q)) } ?: emptyList()

        return state.copy(
            tiles = refreshedTiles,
            selectedTileId = null,
            hintPair = null,
            blockedTileId = null,
            isStalemate = isStuck(refreshedTiles, availablePairs),
            availablePairsCount = availablePairs.size,
            plan = plan,
        )
    }

    /**
     * No free pair, tiles left. An unclearable board with pairs still free (e.g. two matching tiles
     * stacked) plays on; the reshuffle then fixes it.
     */
    fun isStuck(tiles: List<BoardTile>, availablePairs: List<Pair<Int, Int>>): Boolean =
        availablePairs.isEmpty() && tiles.any { !it.isRemoved }

    /** Exhaustive, so only for the few tiles at a board's end. Null if none clears it. */
    fun clearingOrder(tiles: List<BoardTile>): List<Pair<Int, Int>>? {
        val board = recalculateFreedom(tiles)
        if (board.none { !it.isRemoved }) return emptyList()
        for (pair in findAvailablePairs(board)) {
            val after = board.map { if (it.id == pair.first || it.id == pair.second) it.copy(isRemoved = true) else it }
            clearingOrder(after)?.let { return listOf(pair) + it }
        }
        return null
    }

    fun recalculateFreedom(tiles: List<BoardTile>): List<BoardTile> {
        val activePositions = tiles.asSequence().filter { !it.isRemoved }.map { it.position }.toSet()
        return tiles.map { tile ->
            if (tile.isRemoved) {
                tile.copy(isFree = false)
            } else {
                val free = BoardSolvabilityGenerator.isTileFree(tile.position, activePositions)
                tile.copy(isFree = free)
            }
        }
    }

    fun findAvailablePairs(tiles: List<BoardTile>): List<Pair<Int, Int>> {
        val freeTiles = tiles.filter { !it.isRemoved && it.isFree }
        val pairs = mutableListOf<Pair<Int, Int>>()

        for (i in freeTiles.indices) {
            for (j in (i + 1) until freeTiles.size) {
                if (freeTiles[i].tile.matches(freeTiles[j].tile)) {
                    pairs.add(Pair(freeTiles[i].id, freeTiles[j].id))
                }
            }
        }
        return pairs
    }
}
