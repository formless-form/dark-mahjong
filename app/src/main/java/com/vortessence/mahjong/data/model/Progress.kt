package com.vortessence.mahjong.data.model

/**
 * Everything Dark Mahjong records about the player: how far the level journey has got, which
 * boards have been cleared, and the days whose puzzle was solved (epoch days, for Today's Puzzle's
 * calendar). Deliberately no times, counts, scores or streaks.
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
}

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
