package com.vortessence.mahjong.data.model

data class Move(
    val tileId1: Int,
    val tileId2: Int,
)

data class GameState(
    val constellation: Constellation,
    val tiles: List<BoardTile> = emptyList(),
    val selectedTileId: Int? = null,
    val hintPair: Pair<Int, Int>? = null,
    val moveHistory: List<Move> = emptyList(),
    val isWon: Boolean = false,
    val isStalemate: Boolean = false,
    val availablePairsCount: Int = 0,
    val blockedTileId: Int? = null,
    // Hint or reshuffle used: the win isn't clean (undo is fine)
    val usedHelp: Boolean = false,
    // For the best-clear record (LevelRecord). playMillis is up to the last clock settle (the view
    // model keeps the clock, paused while the board is away)
    val hintsUsed: Int = 0,
    val reshufflesUsed: Int = 0,
    val undosUsed: Int = 0,
    val playMillis: Long = 0L,
    // A clearing order hints follow (from the deal, reshuffle or hint solver). Not saved; a restored
    // game finds one again
    val plan: List<Move> = emptyList(),
    // Original deal for replay: faces by tile id, and its plan. Empty for saves predating this
    val dealt: List<Tile> = emptyList(),
    val dealtPlan: List<Move> = emptyList(),
) {
    val remainingTilesCount: Int get() = tiles.count { !it.isRemoved }

    /** Two or three clearable pairs left, all on the lowest layer: the game offers to finish. */
    val canFinish: Boolean get() = finishPlan != null

    val finishPlan: List<Pair<Int, Int>>? by lazy {
        val left = tiles.filter { !it.isRemoved }
        val ground = tiles.minOfOrNull { it.position.z } ?: return@lazy null
        if (isWon || left.size !in FINISH_MIN_TILES..FINISH_MAX_TILES || left.any { it.position.z != ground }) {
            null
        } else {
            com.vortessence.mahjong.engine.MahjongGameEngine.clearingOrder(tiles)
        }
    }
    val totalTilesCount: Int get() = tiles.size
}

// 2-3 pairs left (one pair is just a tap)
private const val FINISH_MIN_TILES = 4
private const val FINISH_MAX_TILES = 6
