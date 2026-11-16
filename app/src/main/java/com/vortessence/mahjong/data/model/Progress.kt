package com.vortessence.mahjong.data.model

import java.time.LocalDate
import java.time.YearMonth

/** puzzleDaysSolved are epoch days. Deliberately no scores or streaks; a full month earns brocade. */
data class Progress(
    val highestLevelReached: Int = 1,
    val completedConstellations: Set<String> = emptySet(),
    val puzzleDaysSolved: Set<Long> = emptySet(),
    // No hints, reshuffles or (since 1.4.16) undos: the gold ribbon
    val cleanConstellations: Set<String> = emptySet(),
    // One earned per first clean clear, at most MAX_HINTS
    val hints: Int = 0,
    // Best clear by layout id
    val records: Map<String, LevelRecord> = emptyMap(),
) {
    /** Counted by layout, so reordering levels can't change it; all of them earns Imperial. */
    fun journeyLevelsCleared(levelIds: List<String>): Int = levelIds.count { it in completedConstellations }

    /** Sealed on the calendar. */
    fun fullPuzzleMonths(): Set<YearMonth> =
        puzzleDaysSolved.map { YearMonth.from(LocalDate.ofEpochDay(it)) }.toSet()
            .filter { isFullPuzzleMonth(it) }.toSet()

    fun isFullPuzzleMonth(month: YearMonth): Boolean =
        (1..month.lengthOfMonth()).all { month.atDay(it).toEpochDay() in puzzleDaysSolved }

    /** For the month [today] (an epoch day) falls in. */
    fun brocadeProgress(today: Long): BrocadeProgress {
        val date = LocalDate.ofEpochDay(today)
        val month = YearMonth.from(date)
        val solved = (1..month.lengthOfMonth()).count { month.atDay(it).toEpochDay() in puzzleDaysSolved }
        // An earlier day unsolved: this month can't be full
        val missed = (1 until date.dayOfMonth).any { month.atDay(it).toEpochDay() !in puzzleDaysSolved }
        return BrocadeProgress(fullPuzzleMonths().isNotEmpty(), solved, month.lengthOfMonth(), month, missed)
    }
}

/** [earned] is for good once any month was full; [missed]: an earlier day this month unsolved. */
data class BrocadeProgress(
    val earned: Boolean,
    val solved: Int,
    val days: Int,
    val month: YearMonth,
    val missed: Boolean,
)

/** A layout's best clear. [millis] excludes time away. */
data class LevelRecord(val millis: Long, val hints: Int, val reshuffles: Int, val undos: Int) {
    /**
     * Less help, then fewer undos, then quicker. A time of 0 (test builds before 2026-11-14 saved 0)
     * loses to any real time.
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

    /** The gold ribbon. */
    val clean: Boolean get() = hints == 0 && reshuffles == 0 && undos == 0
    /** "3:42", or "1:05:09" past an hour; "–" for no time. */
    val time: String get() {
        if (millis <= 0) return "–"
        val s = millis / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}

const val MAX_HINTS = 6
