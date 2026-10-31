package com.vortessence.mahjong

import com.vortessence.mahjong.data.model.Difficulty
import com.vortessence.mahjong.data.model.SavedGameState
import com.vortessence.mahjong.engine.TodaysPuzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TodaysPuzzleTest {

    private val saturday = LocalDate.of(2026, 10, 31).toEpochDay()

    @Test
    fun aDayIsAlwaysTheSamePuzzle() {
        val a = TodaysPuzzle.dealOn(saturday)
        val b = TodaysPuzzle.dealOn(saturday)
        assertEquals(a.constellation.id, b.constellation.id)
        assertEquals(a.tiles.map { it.tile }, b.tiles.map { it.tile })
    }

    @Test
    fun theWeekRunsGentleToHard() {
        val monday = LocalDate.of(2026, 11, 2).toEpochDay()
        val expected = listOf(
            Difficulty.EASY, Difficulty.MEDIUM, Difficulty.MEDIUM, Difficulty.HARD, Difficulty.HARD,
            Difficulty.EXPERT, Difficulty.EXPERT,
        )
        for (i in 0 until 7) {
            assertEquals(expected[i], TodaysPuzzle.difficultyOn(monday + i))
            assertEquals(expected[i], TodaysPuzzle.layoutOn(monday + i).difficulty)
        }
    }

    // Two days of the same difficulty in a week never share a layout, and over a year every
    // layout of each difficulty comes round
    @Test
    fun noLayoutTwiceInAWeek() {
        val monday = LocalDate.of(2026, 11, 2).toEpochDay()
        val seen = mutableSetOf<String>()
        for (week in 0 until 52) {
            val ids = (0 until 7).map { TodaysPuzzle.layoutOn(monday + week * 7 + it).id }
            assertEquals("week $week: $ids", 7, ids.toSet().size)
            seen += ids
        }
        assertTrue("${seen.size} layouts seen in a year", seen.size >= 40)
    }

    @Test
    fun aDayStartsAtFourInTheMorning() {
        assertEquals(saturday, TodaysPuzzle.today(LocalDateTime.of(2026, 11, 1, 3, 59)))
        assertEquals(saturday + 1, TodaysPuzzle.today(LocalDateTime.of(2026, 11, 1, 4, 0)))
    }

    @Test
    fun aSavedPuzzleKeepsItsDay() {
        val state = TodaysPuzzle.dealOn(saturday)
        val restored = SavedGameState.deserialize(SavedGameState.fromGameState(state, null, puzzleDay = saturday).serialize())!!
        assertEquals(saturday, restored.puzzleDay)
        assertEquals(state.tiles.map { it.tile }, restored.toGameState().tiles.map { it.tile })
    }
}
