package com.vortessence.mahjong

import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.SavedGameState
import com.vortessence.mahjong.data.model.TileSuit
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.engine.TileTapResult
import com.vortessence.mahjong.engine.TutorialAction
import com.vortessence.mahjong.engine.TutorialScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialTest {

    private val tutorial = ConstellationRepository.tutorialConstellation

    @Test
    fun testTutorialIsLevelZero() {
        assertEquals(tutorial, ConstellationRepository.getLevel(0))
        assertEquals("jade_pillar", ConstellationRepository.getLevel(1).id)
        assertEquals(tutorial, ConstellationRepository.constellations.first())
    }

    @Test
    fun testTutorialBoardIsAlwaysTheSame() {
        val first = MahjongGameEngine.startNewGame(tutorial).tiles.map { it.tile }
        repeat(5) {
            assertEquals(first, MahjongGameEngine.startNewGame(tutorial).tiles.map { it.tile })
        }
    }

    @Test
    fun testScriptedStepsCanBeCompleted() {
        var state = MahjongGameEngine.startNewGame(tutorial)
        assertEquals(0, TutorialScript.resumeStepFor(state))

        for (step in TutorialScript.steps) {
            when (step.action) {
                TutorialAction.CONTINUE -> Unit
                TutorialAction.PINCH_ZOOM -> {
                    assertFalse(TutorialScript.isZoomStepComplete(step, zoomedIn = false))
                    assertTrue(TutorialScript.isZoomStepComplete(step, zoomedIn = true))
                }
                TutorialAction.ZOOM_OUT -> {
                    assertFalse(TutorialScript.isZoomStepComplete(step, zoomedIn = true))
                    assertTrue(TutorialScript.isZoomStepComplete(step, zoomedIn = false))
                }
                TutorialAction.TAP_BLOCKED -> {
                    val target = step.targetTileIds.single()
                    val (newState, result) = MahjongGameEngine.handleTileTap(state, target)
                    assertTrue("${step.title}: target must be blocked", result is TileTapResult.Blocked)
                    assertTrue(TutorialScript.isStepComplete(step, result, newState))
                    state = newState
                }
                TutorialAction.MATCH_PAIR -> {
                    val (a, b) = step.targetTileIds.toList()
                    val tiles = state.tiles.associateBy { it.id }
                    assertTrue("${step.title}: targets must be free", tiles.getValue(a).isFree && tiles.getValue(b).isFree)
                    val (selected, _) = MahjongGameEngine.handleTileTap(state, a)
                    val (matched, result) = MahjongGameEngine.handleTileTap(selected, b)
                    assertTrue("${step.title}: targets must match", result is TileTapResult.Matched)
                    assertTrue(TutorialScript.isStepComplete(step, result, matched))
                    state = matched
                }
            }
        }

        assertEquals(8, state.remainingTilesCount)
        assertNull(TutorialScript.resumeStepFor(MahjongGameEngine.handleTileTap(
            MahjongGameEngine.handleTileTap(state, 4).first, 11
        ).first))
        assertTrue("Free play must never dead-end", alwaysWinnable(state))
    }

    @Test
    fun testSeasonAndFlowerStepsUseDifferentTiles() {
        val board = MahjongGameEngine.startNewGame(tutorial).tiles.associateBy { it.id }
        for (suit in listOf(TileSuit.SEASON, TileSuit.FLOWER)) {
            val step = TutorialScript.steps.single { s ->
                s.action == TutorialAction.MATCH_PAIR && s.targetTileIds.all { board.getValue(it).tile.suit == suit }
            }
            val (a, b) = step.targetTileIds.map { board.getValue(it).tile }
            assertFalse("$suit step should show two different tiles", a == b)
        }
    }

    @Test
    fun testResumeStepAfterFirstPair() {
        val start = MahjongGameEngine.startNewGame(tutorial)
        val state = start.copy(tiles = start.tiles.map { if (it.id == 0 || it.id == 3) it.copy(isRemoved = true) else it })
        val resumed = TutorialScript.steps[TutorialScript.resumeStepFor(state)!!]
        assertEquals("Seasons", resumed.title)
    }

    // The board as it stands when [index] is shown: every guided pair before it matched
    private fun boardAtStep(index: Int): GameState {
        val start = MahjongGameEngine.startNewGame(tutorial)
        val gone = TutorialScript.steps.take(index).filter { it.action == TutorialAction.MATCH_PAIR }.flatMap { it.targetTileIds }.toSet()
        return start.copy(tiles = MahjongGameEngine.recalculateFreedom(start.tiles.map { if (it.id in gone) it.copy(isRemoved = true) else it }))
    }

    private fun roundTrip(state: GameState, step: Int?): SavedGameState =
        SavedGameState.deserialize(SavedGameState.fromGameState(state, 0, step).serialize())!!

    @Test
    fun testEveryStepResumesWhereItWasSaved() {
        for (index in TutorialScript.steps.indices) {
            val state = boardAtStep(index)
            val saved = roundTrip(state, index)
            assertEquals(TutorialScript.steps[index].title, index, TutorialScript.resumeStep(saved.toGameState(), saved.tutorialStep))
        }
        val free = roundTrip(boardAtStep(TutorialScript.steps.size), SavedGameState.FREE_PLAY)
        assertNull(TutorialScript.resumeStep(free.toGameState(), free.tutorialStep))
    }

    @Test
    fun testSavesWithoutAStepWorkItOutFromTheTiles() {
        val state = boardAtStep(4)
        // A save from before steps were kept has six parts
        val old = SavedGameState.fromGameState(state, 0).serialize().split("|").take(6).joinToString("|")
        val saved = SavedGameState.deserialize(old)!!
        assertNull(saved.tutorialStep)
        assertEquals(TutorialScript.resumeStepFor(state), TutorialScript.resumeStep(saved.toGameState(), null))
        // A saved step that disagrees with the tiles is not trusted
        assertEquals(TutorialScript.resumeStepFor(state), TutorialScript.resumeStep(state, 1))
    }

    @Test
    fun testPairStepsAlreadyMatchedAreSkipped() {
        val flowers = TutorialScript.steps.indexOfFirst { it.title == "Flowers" }
        val start = MahjongGameEngine.startNewGame(tutorial)
        val targets = TutorialScript.steps[flowers].targetTileIds
        val state = start.copy(tiles = start.tiles.map { if (it.id in targets) it.copy(isRemoved = true) else it })
        val resumed = TutorialScript.resumeStep(state, null)!!
        assertTrue(resumed < flowers)
        assertFalse(flowers == TutorialScript.nextStep(flowers - 1, state))
    }

    private fun alwaysWinnable(state: GameState): Boolean {
        if (state.remainingTilesCount == 0) return true
        val pairs = MahjongGameEngine.findAvailablePairs(state.tiles)
        if (pairs.isEmpty()) return false
        return pairs.all { (a, b) ->
            val (selected, _) = MahjongGameEngine.handleTileTap(state, a)
            alwaysWinnable(MahjongGameEngine.handleTileTap(selected, b).first)
        }
    }
}
