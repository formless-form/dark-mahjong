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
    // An order the pairs can be taken in to clear the board, which hints follow: from the deal or
    // the reshuffle, or found by the hint solver. Not saved; a restored game finds one again
    val plan: List<Move> = emptyList(),
) {
    val remainingTilesCount: Int get() = tiles.count { !it.isRemoved }
    val totalTilesCount: Int get() = tiles.size
}
