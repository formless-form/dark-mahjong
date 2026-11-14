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
        .filter { isWideBoard(MahjongGameEngine.startNewGame(it, Random(1)).tiles, it.onSide) }
        .map { it.id }
        .toSet()

    // Only the boards that play much bigger on their side ask to turn the phone: none of the
    // levels (chosen for an upright phone) and not the tutorial
    @Test
    fun onlyTheWidestLayoutsAreWide() {
        assertEquals(setOf("pagoda") + WIDE_LAYOUTS + ON_SIDE, wide())
    }

    // Layouts says "Wide" or "Tall" only of a clearly wide or tall board: most are about even
    @Test
    fun layoutsAreLabelledByTheirShape() {
        val shapes = ConstellationRepository.constellations.filter { !it.onSide }.groupBy({ layoutShape(it.positions) }, { it.id })
        assertEquals(setOf("pagoda", "classic_turtle", "mini_turtle") + WIDE_LAYOUTS, shapes[LayoutShape.WIDE]!!.toSet())
        assertEquals(LayoutShape.TALL, layoutShape(ConstellationRepository.getById("jade_pillar")!!.positions))
        // Fence and Heart: wide and tall in the old labels, about even
        assertEquals(LayoutShape.EVEN, layoutShape(ConstellationRepository.getById("mah_828570775")!!.positions))
        assertEquals(LayoutShape.EVEN, layoutShape(ConstellationRepository.getById("mah_2283982827")!!.positions))
    }

    // Mah's boards too small upright, played on their side (1.4.16): 47 of them; and the three wide
    // boards made for the Journey's last twelve levels
    private val ON_SIDE = ConstellationRepository.constellations.filter { it.onSide }.map { it.id }.toSet().also { check(it.size == 50) }

    // The layouts made wide for Layouts (WideLayouts, ThemedLayouts): all of them wide enough to ask
    private val WIDE_LAYOUTS = setOf(
        "dragon_boat", "great_wall", "feast_table", "sleeping_cat", "twin_peaks", "stone_bridge",
        // ThemedLayouts' wide ten
        "steam_train", "blue_whale", "city_skyline", "lotus_pond", "junk_ship", "red_fox", "crane_in_flight", "comet",
        "koi_carp", "moon_bridge",
    )
}
