package com.vortessence.mahjong.data.model

/**
 * Everything Dark Mahjong records about the player: how far the level journey has got and
 * which boards have been cleared. Deliberately no times, counts or scores.
 */
data class Progress(
    val highestLevelReached: Int = 1,
    val completedConstellations: Set<String> = emptySet(),
)
