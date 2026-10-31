package com.vortessence.mahjong.data.repository

import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.Difficulty
import com.vortessence.mahjong.data.model.TilePosition

/**
 * Wide layouts, for Layouts (not the levels): boards two and a half to six times wider than tall,
 * which play best with the phone on its side. Designed 2026-10-31; each row is a layer ([z]), a
 * row of the board ([y]) and the tiles along it ([x]), in the half-tile units of every layout.
 * Their difficulty labels come from measuring them (LayoutDifficulty), like every layout's.
 */
internal object WideLayouts {
    val layouts: List<Constellation> by lazy {
        listOf(
            dragonBoat(),
            greatWall(),
            feastTable(),
            sleepingCat(),
            twinPeaks(),
            stoneBridge(),
        )
    }

    // 112 tiles
    private fun dragonBoat() = layout(
        "dragon_boat", "Dragon Boat",
        "A dragon boat at full stroke, its dragon head rearing at the bow and a drum keeping time.",
    ) {
        row(z = 0, y = 0, 0, 30, 32, 34)
        row(z = 0, y = 2, 0, 2, 30, 32, 34)
        row(z = 0, y = 4, 2, 32)
        row(z = 0, y = 6, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 8, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30)
        row(z = 0, y = 10, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26)
        row(z = 1, y = 0, 0, 30, 32)
        row(z = 1, y = 2, 30, 32)
        row(z = 1, y = 6, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28)
        row(z = 1, y = 8, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28)
        row(z = 1, y = 10, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 2, y = 6, 6, 10, 14, 16, 18, 20, 24, 28)
        row(z = 2, y = 8, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26)
        row(z = 3, y = 6, 16, 18)
        row(z = 3, y = 8, 14, 16, 18, 20)
    }

    // 82 tiles
    private fun greatWall() = layout(
        "great_wall", "Great Wall",
        "A long fortified wall with watchtowers and a raised central keep.",
    ) {
        row(z = 0, y = 4, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 1, y = 4, 0, 8, 10, 12, 14, 16, 18, 20, 22, 24, 32)
        row(z = 1, y = 6, 0, 8, 10, 12, 14, 16, 18, 20, 22, 24, 32)
        row(z = 2, y = 4, 12, 14, 16, 18, 20)
        row(z = 3, y = 4, 14, 16, 18)
        row(z = 4, y = 4, 16)
    }

    // 96 tiles
    private fun feastTable() = layout(
        "feast_table", "Feast Table",
        "A massive banquet table with benches and a raised centrepiece.",
    ) {
        row(z = 0, y = 2, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 4, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 6, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28)
        row(z = 0, y = 8, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 10, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 1, y = 4, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 1, y = 6, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 2, y = 4, 14, 16, 18, 20)
    }

    // 120 tiles
    private fun sleepingCat() = layout(
        "sleeping_cat", "Sleeping Cat",
        "A cat stretched out in a warm patch of sun, ears up, paws forward, tail curled over its back.",
    ) {
        row(z = 0, y = 0, 4, 8, 34)
        row(z = 0, y = 2, 2, 4, 6, 8, 10, 20, 22, 24, 26, 28, 34)
        row(z = 0, y = 4, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34)
        row(z = 0, y = 6, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 14, 16, 18, 20, 22, 24, 26, 28, 30)
        row(z = 0, y = 10, 0, 2, 4, 18, 20, 26, 28)
        row(z = 1, y = 2, 4, 6, 8, 22, 24)
        row(z = 1, y = 4, 4, 6, 8, 14, 16, 18, 20, 22, 24, 26, 28)
        row(z = 1, y = 6, 4, 6, 8, 14, 16, 18, 20, 22, 24, 26, 28)
        row(z = 1, y = 8, 16, 18, 20, 22, 24, 26)
        row(z = 2, y = 2, 6)
        row(z = 2, y = 4, 6, 16, 18, 20, 22, 24, 26)
        row(z = 2, y = 6, 18, 20, 22, 24, 26)
        row(z = 3, y = 4, 18, 20, 22, 24)
        row(z = 3, y = 6, 20, 22)
    }

    // 64 tiles
    private fun twinPeaks() = layout(
        "twin_peaks", "Twin Peaks",
        "A wide mountain range with two dominant peaks rising above foothills.",
    ) {
        row(z = 0, y = 4, 6, 8, 10, 14, 16, 18, 20, 24, 26, 28)
        row(z = 0, y = 6, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34)
        row(z = 1, y = 4, 8, 14, 16, 18, 20, 26)
        row(z = 1, y = 6, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28)
        row(z = 2, y = 4, 8, 16, 18, 26)
    }

    // 58 tiles
    private fun stoneBridge() = layout(
        "stone_bridge", "Stone Bridge",
        "A broad river bridge with fortified ends and a raised central arch.",
    ) {
        row(z = 0, y = 4, 16, 18, 20)
        row(z = 0, y = 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36)
        row(z = 0, y = 10, 0, 2, 4, 6, 30, 32, 34, 36)
        row(z = 1, y = 4, 16, 18, 20)
        row(z = 1, y = 6, 10, 12, 14, 22, 24, 26)
        row(z = 1, y = 8, 0, 2, 34, 36)
        row(z = 2, y = 4, 16, 18, 20)
        row(z = 3, y = 4, 18)
    }

    private fun layout(id: String, name: String, description: String, rows: MutableList<TilePosition>.() -> Unit) =
        Constellation(
            id = id,
            name = name,
            // Provisional: replaced by the measured label
            difficulty = Difficulty.HARD,
            description = description,
            positions = mutableListOf<TilePosition>().apply(rows),
        )

    private fun MutableList<TilePosition>.row(z: Int, y: Int, vararg xs: Int) {
        for (x in xs) add(TilePosition(x.toFloat(), y.toFloat(), z))
    }
}
