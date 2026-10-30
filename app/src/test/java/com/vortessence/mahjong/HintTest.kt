package com.vortessence.mahjong

import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.SavedGameState
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.HintSolver
import com.vortessence.mahjong.engine.MahjongGameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HintTest {

    private val layouts = ConstellationRepository.levelConstellations

    private fun take(state: GameState, pair: Pair<Int, Int>): GameState {
        val (selected, _) = MahjongGameEngine.handleTileTap(state, pair.first)
        return MahjongGameEngine.handleTileTap(selected, pair.second).first
    }

    // Plays [state] to the end by hints alone; true if the board was cleared
    private fun followHints(start: GameState): Boolean {
        var state = start
        while (!state.isWon) {
            val hint = MahjongGameEngine.findHint(state) ?: return false
            state = take(state.copy(plan = hint.plan.ifEmpty { state.plan }), hint.pair)
        }
        return true
    }

    @Test
    fun followingHintsClearsEveryNewDeal() {
        for (layout in layouts) {
            repeat(3) { seed ->
                val state = MahjongGameEngine.startNewGame(layout, Random(seed))
                assertTrue("${layout.name} has a plan", state.plan.isNotEmpty())
                assertTrue("${layout.name} (seed $seed) cleared by hints", followHints(state))
            }
        }
    }

    // A player who paired tiles their own way breaks the deal's plan, and a restored game has none:
    // the solver finds one again. Whether the board can still be cleared at all is checked with a
    // long search
    private fun recovery(keepPlan: Boolean): Triple<Int, Int, Long> {
        var won = 0
        var clearable = 0
        var slowest = 0L
        for (layout in layouts) {
            val random = Random(100)
            var state = MahjongGameEngine.startNewGame(layout, random)
            // Ten matches of the player's own, any free pair
            repeat(10) {
                val pairs = MahjongGameEngine.findAvailablePairs(state.tiles)
                if (pairs.isNotEmpty() && !state.isWon) state = take(state, pairs.random(random))
            }
            // A restore keeps the plan in the save; a save from before plans were kept has none
            state = if (keepPlan) SavedGameState.deserialize(SavedGameState.fromGameState(state, null).serialize())!!.toGameState()
            else state.copy(plan = emptyList())
            if (state.isWon || HintSolver.solve(state.tiles, budget = 200_000) == null) continue
            clearable++
            val started = System.nanoTime()
            assertNotNull(MahjongGameEngine.findHint(state))
            slowest = maxOf(slowest, System.nanoTime() - started)
            if (followHints(state)) won++
        }
        return Triple(won, clearable, slowest / 1_000_000)
    }

    @Test
    fun hintsRecoverAfterOwnPlayAndARestore() {
        val (won, clearable, slowest) = recovery(keepPlan = true)
        println("Hints after own play: cleared $won of $clearable clearable, slowest first hint $slowest ms")
        assertTrue("cleared $won of $clearable", won >= clearable * 0.9)
    }

    @Test
    fun hintsRecoverWithoutAPlan() {
        val (won, clearable, slowest) = recovery(keepPlan = false)
        println("Hints without a plan: cleared $won of $clearable clearable, slowest first hint $slowest ms")
        assertTrue("cleared $won of $clearable", won >= clearable * 0.8)
    }

    @Test
    fun hintStaysLitWhileOneOfItsTilesIsPicked() {
        val state = MahjongGameEngine.startNewGame(layouts.first(), Random(3))
        val pair = MahjongGameEngine.findHint(state)!!.pair
        val hinted = state.copy(hintPair = pair)
        val picked = MahjongGameEngine.handleTileTap(hinted, pair.first).first
        assertEquals(pair, picked.hintPair)
        val other = hinted.tiles.first { it.isFree && it.id != pair.first && it.id != pair.second }
        assertNull(MahjongGameEngine.handleTileTap(hinted, other.id).first.hintPair)
    }

    @Test
    fun repeatedHintsMoveOnToAnotherSafePair() {
        val state = MahjongGameEngine.startNewGame(layouts.last(), Random(5))
        val first = MahjongGameEngine.findHint(state)!!.pair
        val second = MahjongGameEngine.findHint(state.copy(hintPair = first))!!.pair
        val safe = state.plan.count { m -> state.tiles.filter { it.isFree }.map { it.id }.let { m.tileId1 in it && m.tileId2 in it } }
        if (safe > 1) assertFalse(first == second)
    }
}
