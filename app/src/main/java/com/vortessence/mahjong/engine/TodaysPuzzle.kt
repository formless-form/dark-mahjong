package com.vortessence.mahjong.engine

import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.Difficulty
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.repository.ConstellationRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Today's Puzzle: one board a day, the same for everyone and worked out from the date alone, so it
 * needs nothing downloaded. The layout comes from the weekday's difficulty (measured, as the
 * Layouts list): gentle on Monday, harder through the week, hardest at the weekend. Each week the
 * layouts of a difficulty are shuffled afresh, so two days sharing one never share a layout; the
 * deal comes from the day, so a layout that comes round again is dealt differently.
 */
object TodaysPuzzle {

    /** Today's puzzle day, as an epoch day: a day runs from 4 am, as the game's others do. */
    fun today(now: LocalDateTime = LocalDateTime.now()): Long = now.minusHours(4).toLocalDate().toEpochDay()

    fun difficultyOn(day: Long): Difficulty = difficultyOn(LocalDate.ofEpochDay(day).dayOfWeek)

    private fun difficultyOn(weekday: DayOfWeek): Difficulty = when (weekday) {
        DayOfWeek.MONDAY -> Difficulty.EASY
        DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY -> Difficulty.MEDIUM
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> Difficulty.HARD
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> Difficulty.EXPERT
    }

    fun layoutOn(day: Long): Constellation {
        val difficulty = difficultyOn(day)
        val pool = ConstellationRepository.constellations
            .filter { it.id != ConstellationRepository.TUTORIAL_ID && it.difficulty == difficulty }
            .sortedBy { it.id }
            .ifEmpty { ConstellationRepository.levelConstellations }
        // Weeks run Monday to Sunday (epoch day 0 was a Thursday); a day's place among the days
        // of its difficulty that week picks from that week's shuffle
        val week = Math.floorDiv(day + 3, 7)
        val slot = DayOfWeek.entries.filter { difficultyOn(it) == difficulty }.indexOf(LocalDate.ofEpochDay(day).dayOfWeek)
        return pool.shuffled(Random(week * 31 + difficulty.ordinal))[slot % pool.size]
    }

    /** The day's deal: always the same for that day. */
    fun dealOn(day: Long): GameState = MahjongGameEngine.startNewGame(layoutOn(day), Random(day * 7919 + 17))
}
