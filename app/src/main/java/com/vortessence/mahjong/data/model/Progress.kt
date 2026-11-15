package com.vortessence.mahjong.data.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything Dark Mahjong records about the player: how far the level journey has got, which
 * boards have been cleared, and the days whose puzzle was solved (epoch days, for Today's Puzzle's
 * calendar). Deliberately no scores and no running streak: a month with every day's puzzle solved
 * is sealed on the calendar, and the first earns the silk brocade table, for good.
 */
data class Progress(
    val highestLevelReached: Int = 1,
    val completedConstellations: Set<String> = emptySet(),
    val puzzleDaysSolved: Set<Long> = emptySet(),
    // Layouts cleared cleanly (no hints, no reshuffles, and since 1.4.16 no undos) since that was
    // first kept: the gold ribbon
    val cleanConstellations: Set<String> = emptySet(),
    // Hints to spend: one earned for each first clean clear, at most MAX_HINTS
    val hints: Int = 0,
    // Each layout's best clear (by its id), kept since that was first recorded
    val records: Map<String, LevelRecord> = emptyMap(),
) {
    /**
     * How many of the Journey's levels ([levelIds]) have been cleared, played as levels or not.
     * Counted by layout, so no reordering of the levels can change it: all of them earns Imperial.
     */
    fun journeyLevelsCleared(levelIds: List<String>): Int = levelIds.count { it in completedConstellations }

    /** The months with every day's puzzle solved: sealed on the calendar. */
    fun fullPuzzleMonths(): Set<YearMonth> =
        puzzleDaysSolved.map { YearMonth.from(LocalDate.ofEpochDay(it)) }.toSet()
            .filter { isFullPuzzleMonth(it) }.toSet()

    fun isFullPuzzleMonth(month: YearMonth): Boolean =
        (1..month.lengthOfMonth()).all { month.atDay(it).toEpochDay() in puzzleDaysSolved }

    /** How far this month (the one [today], an epoch day, falls in) has got towards the brocade. */
    fun brocadeProgress(today: Long): BrocadeProgress {
        val date = LocalDate.ofEpochDay(today)
        val month = YearMonth.from(date)
        val solved = (1..month.lengthOfMonth()).count { month.atDay(it).toEpochDay() in puzzleDaysSolved }
        // A day before today left unsolved: this month can't be full now
        val missed = (1 until date.dayOfMonth).any { month.atDay(it).toEpochDay() !in puzzleDaysSolved }
        return BrocadeProgress(fullPuzzleMonths().isNotEmpty(), solved, month.lengthOfMonth(), month, missed)
    }
}

/**
 * The silk brocade table, earned by solving every daily puzzle of a month: [earned] for good once
 * one month was full; otherwise [solved] of this [month]'s [days], and whether a day was [missed].
 */
data class BrocadeProgress(
    val earned: Boolean,
    val solved: Int,
    val days: Int,
    val month: YearMonth,
    val missed: Boolean,
)

/**
 * A layout's best clear: the cleanest (fewest hints and reshuffles, then fewest undos), and of
 * those the quickest. [millis] is time spent playing, never counting time away.
 */
data class LevelRecord(val millis: Long, val hints: Int, val reshuffles: Int, val undos: Int) {
    /**
     * Better than [other]: less help (hints and reshuffles), then fewer undos, then quicker. A time
     * of 0 is no time at all (test builds before 2026-11-14 saved 0 for every clear), so any real
     * time beats it.
     */
    fun beats(other: LevelRecord): Boolean =
        compareValuesBy(this, other, { it.hints + it.reshuffles }, { it.undos }, { if (it.millis > 0) it.millis else Long.MAX_VALUE }) < 0

    /** Stored as "millis,hints,reshuffles,undos". */
    fun encode() = "$millis,$hints,$reshuffles,$undos"

    companion object {
        fun decode(raw: String): LevelRecord? {
            val p = raw.split(",").mapNotNull { it.toLongOrNull() }.takeIf { it.size == 4 } ?: return null
            return LevelRecord(p[0], p[1].toInt(), p[2].toInt(), p[3].toInt())
        }
    }

    /** No hints, no reshuffles and no undos: the gold ribbon. */
    val clean: Boolean get() = hints == 0 && reshuffles == 0 && undos == 0
    /** "3:42", or "1:05:09" past an hour; "–" for no time. */
    val time: String get() {
        if (millis <= 0) return "–"
        val s = millis / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}

const val MAX_HINTS = 6
