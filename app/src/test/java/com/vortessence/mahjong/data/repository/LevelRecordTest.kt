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

    // The bug in the first test build: the win stopped the clock and its time was lost (0:00)
    @Test
    fun aWinRecordsTheTimePlayed() {
        com.vortessence.mahjong.ui.viewmodel.MahjongViewModel.dealContext = kotlin.coroutines.EmptyCoroutineContext
        try {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            val vm = com.vortessence.mahjong.ui.viewmodel.MahjongViewModel(app)
            looper.idle()
            vm.startLevel(1)
            looper.idle()
            looper.idleFor(java.time.Duration.ofSeconds(42))
            val state = vm.gameState.value!!
            for (m in state.plan) {
                vm.onTileTapped(m.tileId1)
                vm.onTileTapped(m.tileId2)
                looper.idle()
            }
            assertTrue(vm.gameState.value!!.isWon)
            val id = state.constellation.id
            // The win is written in the background: the main thread is let run while it's awaited
            val repo = PreferencesRepository(app)
            var record: LevelRecord? = null
            repeat(100) {
                if (record == null) {
                    looper.idle()
                    record = runBlocking { kotlinx.coroutines.withTimeoutOrNull(100) { repo.progressFlow.first { p -> p.records.containsKey(id) } } }?.records?.get(id)
                }
            }
            val found = record!!
            assertTrue("recorded ${found.millis} ms", found.millis >= 42_000)
            assertTrue(found.clean)
        } finally {
            com.vortessence.mahjong.ui.viewmodel.MahjongViewModel.dealContext = kotlinx.coroutines.Dispatchers.Default
        }
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
