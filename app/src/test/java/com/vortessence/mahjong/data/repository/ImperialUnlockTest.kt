package com.vortessence.mahjong.data.repository

import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.ui.screens.ImperialProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Earned by clearing every Journey level, counted by layout. */
class ImperialUnlockTest {
    private val levels = ConstellationRepository.levelConstellations.map { it.id }

    private fun imperial(progress: Progress) = ImperialProgress(progress.journeyLevelsCleared(levels), levels.size)

    @Test
    fun theJourneyIsAHundredLevels() = assertEquals(100, levels.size)

    @Test
    fun nothingClearedIsLocked() {
        val p = imperial(Progress())
        assertEquals(0, p.cleared)
        assertFalse(p.earned)
    }

    @Test
    fun onlyJourneyLayoutsCount() {
        val others = ConstellationRepository.constellations.map { it.id }.filterNot { it in levels }.toSet()
        val p = imperial(Progress(completedConstellations = others + levels.take(36)))
        assertEquals(36, p.cleared)
        assertFalse(p.earned)
    }

    @Test
    fun oneShortIsStillLocked() = assertFalse(imperial(Progress(completedConstellations = levels.drop(1).toSet())).earned)

    // Reaching level 101 under an old numbering without clearing the new boards doesn't earn it
    @Test
    fun everyLevelClearedEarnsIt() {
        assertTrue(imperial(Progress(completedConstellations = levels.toSet())).earned)
        assertFalse(imperial(Progress(highestLevelReached = 101, completedConstellations = levels.take(88).toSet())).earned)
    }
}
