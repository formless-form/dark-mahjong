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
 * One board a day, the same for everyone, from the date alone (nothing downloaded). Difficulty rises
 * through the week; each week's per-difficulty shuffle keeps days sharing one on different layouts,
 * and the deal comes from the day, so a repeat layout is dealt differently.
 */
object TodaysPuzzle {

    /** Epoch day; a day runs from 4 am, as elsewhere in the game. */
    fun today(now: LocalDateTime = LocalDateTime.now()): Long = now.minusHours(4).toLocalDate().toEpochDay()

    fun nextDayStarts(day: Long): LocalDateTime = LocalDate.ofEpochDay(day + 1).atTime(4, 0)

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
        // Weeks run Monday to Sunday (epoch day 0 was a Thursday)
        val week = Math.floorDiv(day + 3, 7)
        val slot = DayOfWeek.entries.filter { difficultyOn(it) == difficulty }.indexOf(LocalDate.ofEpochDay(day).dayOfWeek)
        return pool.shuffled(Random(week * 31 + difficulty.ordinal))[slot % pool.size]
    }

    fun dealOn(day: Long): GameState = MahjongGameEngine.startNewGame(layoutOn(day), Random(day * 7919 + 17))
}
