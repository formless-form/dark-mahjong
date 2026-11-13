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
    // Layouts cleared cleanly (no hints, no reshuffles) since that was first kept: the gold ribbon
    val cleanConstellations: Set<String> = emptySet(),
    // Hints to spend: one earned for each first clean clear, at most MAX_HINTS
    val hints: Int = 0,
    // Each layout's best clear (by its id), kept since that was first recorded
    val records: Map<String, LevelRecord> = emptyMap(),
)

/**
 * A layout's best clear: the cleanest (fewest hints and reshuffles, then fewest undos), and of
 * those the quickest. [millis] is time spent playing, never counting time away.
 */
data class LevelRecord(val millis: Long, val hints: Int, val reshuffles: Int, val undos: Int) {
    /** No hints and no reshuffles: the gold ribbon. */
    val clean: Boolean get() = hints == 0 && reshuffles == 0
    /** Clean, and not one move undone either. */
    val perfect: Boolean get() = clean && undos == 0
    /** "3:42", or "1:05:09" past an hour. */
    val time: String get() {
        val s = millis / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}

const val MAX_HINTS = 6
