package com.vortessence.mahjong.data.model

import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine

data class SavedTile(
    val id: Int,
    val suit: TileSuit,
    val value: Int,
    val position: TilePosition,
    val isRemoved: Boolean,
)

data class ContinueInfo(
    val title: String,
    val subtitle: String,
)

data class SavedGameState(
    val constellationId: String,
    val levelIndex: Int?,
    val movesCount: Int,
    val tiles: List<SavedTile>,
    val moveHistory: List<Move>,
    // The tutorial's step: an index into TutorialScript.steps, FREE_PLAY once the guided part is
    // over, or null (any other board, or a save from before steps were kept)
    val tutorialStep: Int? = null,
) {
    fun toGameState(): GameState {
        val constellation = ConstellationRepository.getById(constellationId)
        val boardTiles = tiles.map { st ->
            BoardTile(
                id = st.id,
                tile = Tile(st.suit, st.value),
                position = st.position,
                isRemoved = st.isRemoved,
                isFree = false,
            )
        }
        val tilesWithFreedom = MahjongGameEngine.recalculateFreedom(boardTiles)
        val availablePairs = MahjongGameEngine.findAvailablePairs(tilesWithFreedom)
        val remainingCount = tilesWithFreedom.count { !it.isRemoved }
        val isWon = remainingCount == 0
        val isStalemate = MahjongGameEngine.isStuck(tilesWithFreedom, availablePairs)

        return GameState(
            constellation = constellation,
            tiles = tilesWithFreedom,
            selectedTileId = null,
            hintPair = null,
            moveHistory = moveHistory,
            movesCount = movesCount,
            isPlaying = true,
            isWon = isWon,
            isStalemate = isStalemate,
            availablePairsCount = availablePairs.size,
            blockedTileId = null,
        )
    }

    fun serialize(): String {
        val levelStr = levelIndex?.toString() ?: ""
        val tilesStr = tiles.joinToString(";") { t ->
            "${t.id},${t.suit.name},${t.value},${t.position.x},${t.position.y},${t.position.z},${if (t.isRemoved) 1 else 0}"
        }
        val movesStr = moveHistory.joinToString(";") { m ->
            "${m.tileId1},${m.tileId2}"
        }
        return listOf(
            constellationId,
            levelStr,
            "", // Unused slot (formerly the timer), kept so the format stays stable
            movesCount.toString(),
            tilesStr,
            movesStr,
            tutorialStep?.toString() ?: "",
        ).joinToString("|")
    }

    companion object {
        const val FREE_PLAY = -1

        fun fromGameState(state: GameState, levelIndex: Int?, tutorialStep: Int? = null): SavedGameState {
            return SavedGameState(
                constellationId = state.constellation.id,
                levelIndex = levelIndex,
                movesCount = state.movesCount,
                tiles = state.tiles.map { bt ->
                    SavedTile(
                        id = bt.id,
                        suit = bt.tile.suit,
                        value = bt.tile.value,
                        position = bt.position,
                        isRemoved = bt.isRemoved,
                    )
                },
                moveHistory = state.moveHistory,
                tutorialStep = tutorialStep,
            )
        }

        fun deserialize(raw: String): SavedGameState? {
            if (raw.isBlank()) return null
            try {
                val parts = raw.split("|")
                if (parts.size < 6) return null

                val constellationId = parts[0]
                if (constellationId.isBlank()) return null
                val levelIndex = parts[1].toIntOrNull()
                val movesCount = parts[3].toIntOrNull() ?: 0

                val tilesRaw = parts[4]
                if (tilesRaw.isBlank()) return null
                val tiles = tilesRaw.split(";").mapNotNull { tStr ->
                    val tParts = tStr.split(",")
                    if (tParts.size >= 7) {
                        val id = tParts[0].toIntOrNull() ?: return@mapNotNull null
                        val suit = try {
                            TileSuit.valueOf(tParts[1])
                        } catch (_: Exception) {
                            return@mapNotNull null
                        }
                        val value = tParts[2].toIntOrNull() ?: return@mapNotNull null
                        val x = tParts[3].toFloatOrNull() ?: return@mapNotNull null
                        val y = tParts[4].toFloatOrNull() ?: return@mapNotNull null
                        val z = tParts[5].toIntOrNull() ?: return@mapNotNull null
                        val isRemoved = tParts[6] == "1"
                        SavedTile(id, suit, value, TilePosition(x, y, z), isRemoved)
                    } else {
                        null
                    }
                }
                if (tiles.isEmpty()) return null

                val movesRaw = parts[5]
                val moves = if (movesRaw.isNotBlank()) {
                    movesRaw.split(";").mapNotNull { mStr ->
                        val mParts = mStr.split(",")
                        if (mParts.size >= 2) {
                            val t1 = mParts[0].toIntOrNull() ?: return@mapNotNull null
                            val t2 = mParts[1].toIntOrNull() ?: return@mapNotNull null
                            Move(t1, t2)
                        } else {
                            null
                        }
                    }
                } else {
                    emptyList()
                }

                return SavedGameState(
                    constellationId = constellationId,
                    levelIndex = levelIndex,
                    movesCount = movesCount,
                    tiles = tiles,
                    moveHistory = moves,
                    tutorialStep = parts.getOrNull(6)?.toIntOrNull(),
                )
            } catch (_: Exception) {
                return null
            }
        }
    }
}
