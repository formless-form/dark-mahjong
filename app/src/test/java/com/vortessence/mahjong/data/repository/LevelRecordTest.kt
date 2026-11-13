package com.vortessence.mahjong.data.repository

import androidx.test.core.app.ApplicationProvider
import com.vortessence.mahjong.data.model.LevelRecord
import com.vortessence.mahjong.data.model.SavedGameState
import com.vortessence.mahjong.engine.MahjongGameEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/** Each layout's best clear: which counts as better, how it's stored, and that saves carry it. */
@RunWith(RobolectricTestRunner::class)
class LevelRecordTest {
    @Test
    fun lessHelpBeatsFewerUndosBeatsQuicker() {
        val clean = LevelRecord(600_000, 0, 0, 4)
        val helped = LevelRecord(60_000, 1, 0, 0)
        assertTrue(clean.beats(helped))
        assertTrue(LevelRecord(600_000, 0, 0, 1).beats(clean))
        assertTrue(LevelRecord(500_000, 0, 0, 4).beats(clean))
        assertFalse(clean.beats(clean))
    }

    @Test
    fun timeReads() {
        assertEquals("3:42", LevelRecord(222_000, 0, 0, 0).time)
        assertEquals("1:05:09", LevelRecord(3_909_000, 0, 0, 0).time)
        assertEquals(LevelRecord(222_000, 1, 2, 3), LevelRecord.decode(LevelRecord(222_000, 1, 2, 3).encode()))
    }

    @Test
    fun aWorseClearNeverReplacesTheBest() = runBlocking {
        val repo = PreferencesRepository(ApplicationProvider.getApplicationContext())
        val id = "record_test_layout"
        repo.recordGameWon(id, null, record = LevelRecord(300_000, 0, 0, 2))
        repo.recordGameWon(id, null, record = LevelRecord(100_000, 1, 0, 0))
        assertEquals(LevelRecord(300_000, 0, 0, 2), repo.progressFlow.first().records[id])
        repo.recordGameWon(id, null, record = LevelRecord(400_000, 0, 0, 0))
        assertEquals(LevelRecord(400_000, 0, 0, 0), repo.progressFlow.first().records[id])
    }

    @Test
    fun aSaveCarriesTheCountsAndTime() {
        val layout = com.vortessence.mahjong.data.repository.ConstellationRepository.levelConstellations.first()
        val state = MahjongGameEngine.startNewGame(layout, Random(1)).copy(hintsUsed = 1, reshufflesUsed = 2, undosUsed = 3, playMillis = 45_000)
        val back = SavedGameState.deserialize(SavedGameState.fromGameState(state, 1).serialize())!!.toGameState()
        assertEquals(listOf(1, 2, 3), listOf(back.hintsUsed, back.reshufflesUsed, back.undosUsed))
        assertEquals(45_000L, back.playMillis)
    }
}
