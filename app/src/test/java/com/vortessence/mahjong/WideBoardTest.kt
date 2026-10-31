package com.vortessence.mahjong

import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.ui.components.LayoutShape
import com.vortessence.mahjong.ui.components.isWideBoard
import com.vortessence.mahjong.ui.components.layoutShape
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class WideBoardTest {

    private fun wide() = ConstellationRepository.constellations
        .filter { isWideBoard(MahjongGameEngine.startNewGame(it, Random(1)).tiles) }
        .map { it.id }
        .toSet()

    // Only the boards that play much bigger on their side ask to turn the phone: none of the
    // levels (chosen for an upright phone) and not the tutorial
    @Test
    fun onlyTheWidestLayoutsAreWide() {
        assertEquals(setOf("pagoda") + WIDE_LAYOUTS, wide())
    }

    // Layouts says "Wide" or "Tall" only of a clearly wide or tall board: most are about even
    @Test
    fun layoutsAreLabelledByTheirShape() {
        val shapes = ConstellationRepository.constellations.groupBy({ layoutShape(it.positions) }, { it.id })
        assertEquals(setOf("pagoda", "classic_turtle", "mini_turtle") + WIDE_LAYOUTS, shapes[LayoutShape.WIDE]!!.toSet())
        assertEquals(LayoutShape.TALL, layoutShape(ConstellationRepository.getById("jade_pillar")!!.positions))
        // Fence and Heart: wide and tall in the old labels, about even
        assertEquals(LayoutShape.EVEN, layoutShape(ConstellationRepository.getById("mah_828570775")!!.positions))
        assertEquals(LayoutShape.EVEN, layoutShape(ConstellationRepository.getById("mah_2283982827")!!.positions))
    }

    // The layouts made wide for Layouts (WideLayouts): all of them wide enough to ask
    private val WIDE_LAYOUTS = setOf("dragon_boat", "great_wall", "feast_table", "sleeping_cat", "twin_peaks", "stone_bridge")
}
