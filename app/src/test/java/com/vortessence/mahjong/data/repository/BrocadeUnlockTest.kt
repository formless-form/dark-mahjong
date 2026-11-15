package com.vortessence.mahjong.data.repository

import com.vortessence.mahjong.data.model.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The silk brocade table is earned by solving every daily puzzle of one calendar month, for good. */
class BrocadeUnlockTest {
    private val november = YearMonth.of(2026, 11)

    private fun days(month: YearMonth, range: IntRange) = range.map { month.atDay(it).toEpochDay() }.toSet()

    private fun day(month: YearMonth, d: Int) = month.atDay(d).toEpochDay()

    @Test
    fun nothingSolvedIsLocked() {
        val p = Progress().brocadeProgress(day(november, 15))
        assertFalse(p.earned)
        assertEquals(0, p.solved)
        assertEquals(30, p.days)
        assertEquals(november, p.month)
    }

    @Test
    fun everyDaySoFarIsOnTrack() {
        val p = Progress(puzzleDaysSolved = days(november, 1..14)).brocadeProgress(day(november, 15))
        assertFalse(p.earned)
        assertFalse(p.missed)
        assertEquals(14, p.solved)
    }

    // Today itself not solved yet is no miss: the day isn't over
    @Test
    fun todayUnsolvedIsNotAMiss() {
        val p = Progress(puzzleDaysSolved = days(november, 1..14)).brocadeProgress(day(november, 15))
        assertFalse(p.missed)
    }

    @Test
    fun aMissedDayIsSaid() {
        val p = Progress(puzzleDaysSolved = days(november, 1..14) - day(november, 9)).brocadeProgress(day(november, 15))
        assertTrue(p.missed)
        assertFalse(p.earned)
    }

    @Test
    fun aFullMonthEarnsIt() {
        val p = Progress(puzzleDaysSolved = days(november, 1..30))
        assertEquals(setOf(november), p.fullPuzzleMonths())
        assertTrue(p.brocadeProgress(day(november, 30)).earned)
    }

    // Earned for good: later months, missed days and all, never take it back
    @Test
    fun earnedForGood() {
        val p = Progress(puzzleDaysSolved = days(november, 1..30) + day(november.plusMonths(1), 2))
        val later = p.brocadeProgress(day(november.plusMonths(1), 20))
        assertTrue(later.earned)
        assertTrue(later.missed)
    }

    // February in a leap year needs its 29th, and a 30-day month only its 30
    @Test
    fun monthLengthsCount() {
        val feb = YearMonth.of(2028, 2)
        assertFalse(Progress(puzzleDaysSolved = days(feb, 1..28)).isFullPuzzleMonth(feb))
        assertTrue(Progress(puzzleDaysSolved = days(feb, 1..29)).isFullPuzzleMonth(feb))
        assertTrue(Progress(puzzleDaysSolved = days(november, 1..30)).isFullPuzzleMonth(november))
    }

    // Days spread over two months, neither full, earn nothing
    @Test
    fun aRunAcrossMonthsIsNotAMonth() {
        val run = (0L until 31L).map { LocalDate.of(2026, 10, 20).toEpochDay() + it }.toSet()
        assertTrue(Progress(puzzleDaysSolved = run).fullPuzzleMonths().isEmpty())
    }
}
