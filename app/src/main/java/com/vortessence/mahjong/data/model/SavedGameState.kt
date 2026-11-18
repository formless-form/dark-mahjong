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
)

data class SavedGameState(
    val constellationId: String,
    val levelIndex: Int?,
    val tiles: List<SavedTile>,
    val moveHistory: List<Move>,
    // Index into TutorialScript.steps, FREE_PLAY after the guided part; null otherwise or in older
    // saves
    val tutorialStep: Int? = null,
    // GameState.plan; empty in older saves
    val plan: List<Move> = emptyList(),
    // GameState.dealt/dealtPlan; empty in older saves
    val dealt: List<Tile> = emptyList(),
    val dealtPlan: List<Move> = emptyList(),
    // Epoch day; null for other boards
    val puzzleDay: Long? = null,
    val usedHelp: Boolean = false,
    // 0 in older saves
    val hintsUsed: Int = 0,
    val reshufflesUsed: Int = 0,
    val undosUsed: Int = 0,
    val playMillis: Long = 0L,
    // GameState.noRecord
    val noRecord: Boolean = false,
) {
    // deserialize only accepts existing layouts
    fun toGameState(): GameState {
        val constellation = requireNotNull(ConstellationRepository.getById(constellationId))
        val boardTiles = tiles.map { st ->
            BoardTile(
                id = st.id,
                tile = Tile(st.suit, st.value),
                position = st.position,
                isRemoved = st.isRemoved,
                isFree = false,
            )
        }
        return MahjongGameEngine.stateOf(constellation, boardTiles, moveHistory, plan)
            .copy(
                dealt = dealt.takeIf { it.size == boardTiles.size } ?: emptyList(), dealtPlan = dealtPlan, usedHelp = usedHelp,
                hintsUsed = hintsUsed, reshufflesUsed = reshufflesUsed, undosUsed = undosUsed, playMillis = playMillis,
                noRecord = noRecord,
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
        fun movesOf(moves: List<Move>) = moves.joinToString(";") { m -> "${m.tileId1},${m.tileId2}" }
        return listOf(
            constellationId,
            levelStr,
            "", // Unused slot (formerly the timer), kept so the format stays stable
            moveHistory.size.toString(), // The move count, unread now; kept for older builds
            tilesStr,
            movesStr,
            tutorialStep?.toString() ?: "",
            movesOf(plan),
            dealt.joinToString(";") { "${it.suit.name},${it.value}" },
            movesOf(dealtPlan),
            puzzleDay?.toString() ?: "",
            if (usedHelp) "1" else "",
            hintsUsed.toString(),
            reshufflesUsed.toString(),
            undosUsed.toString(),
            playMillis.toString(),
            if (noRecord) "1" else "",
        ).joinToString("|")
    }

    companion object {
        const val FREE_PLAY = -1

        fun fromGameState(state: GameState, levelIndex: Int?, tutorialStep: Int? = null, puzzleDay: Long? = null): SavedGameState {
            return SavedGameState(
                constellationId = state.constellation.id,
                levelIndex = levelIndex,
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
                plan = state.plan,
                dealt = state.dealt,
                dealtPlan = state.dealtPlan,
                puzzleDay = puzzleDay,
                usedHelp = state.usedHelp,
                hintsUsed = state.hintsUsed,
                reshufflesUsed = state.reshufflesUsed,
                undosUsed = state.undosUsed,
                playMillis = state.playMillis,
                noRecord = state.noRecord,
            )
        }

        fun deserialize(raw: String): SavedGameState? {
            if (raw.isBlank()) return null
            try {
                val parts = raw.split("|")
                if (parts.size < 6) return null

                val constellationId = parts[0]
                // Layout gone: drop the save
                if (ConstellationRepository.getById(constellationId) == null) return null
                val levelIndex = parts[1].toIntOrNull()

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

                fun moves(raw: String?): List<Move> =
                    if (!raw.isNullOrBlank()) {
                        raw.split(";").mapNotNull { mStr ->
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
                val moves = moves(parts[5])

                return SavedGameState(
                    constellationId = constellationId,
                    levelIndex = levelIndex,
                    tiles = tiles,
                    moveHistory = moves,
                    tutorialStep = parts.getOrNull(6)?.toIntOrNull(),
                    plan = moves(parts.getOrNull(7)),
                    dealt = parts.getOrNull(8).orEmpty().split(";").mapNotNull { f ->
                        val (suit, value) = f.split(",").takeIf { it.size == 2 } ?: return@mapNotNull null
                        val s = TileSuit.entries.firstOrNull { it.name == suit } ?: return@mapNotNull null
                        Tile(s, value.toIntOrNull() ?: return@mapNotNull null)
                    },
                    dealtPlan = moves(parts.getOrNull(9)),
                    puzzleDay = parts.getOrNull(10)?.toLongOrNull(),
                    // Older saves count as clean so far
                    usedHelp = parts.getOrNull(11) == "1",
                    hintsUsed = parts.getOrNull(12)?.toIntOrNull() ?: 0,
                    reshufflesUsed = parts.getOrNull(13)?.toIntOrNull() ?: 0,
                    undosUsed = parts.getOrNull(14)?.toIntOrNull() ?: 0,
                    playMillis = parts.getOrNull(15)?.toLongOrNull() ?: 0L,
                    // Saves before 1.4.16 end at field 11
                    noRecord = parts.getOrNull(16) == "1" || parts.size <= 15,
                )
            } catch (_: Exception) {
                return null
            }
        }
    }
}
