package com.vortessence.mahjong.data.repository

import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.Difficulty
import com.vortessence.mahjong.data.model.TilePosition

/**
 * Twelve layouts made for the Journey (2026-11-14), taking it from 88 levels to 100: three
 * boards played on their side and nine upright, each slotted in by its measured clear rate
 * (LayoutDifficulty). Each raised layer sits half a tile off the one below, so stacks always leave
 * tiles in pairs, and every deal works first time.
 * Each row is a layer ([z]), a row of the board ([y]) and the tiles along it ([x]), in the
 * half-tile units of every layout.
 */
internal object JourneyLayouts {
    val layouts: List<Constellation> by lazy {
        listOf(
            paperBoat(),
            ancientCoin(),
            paperUmbrella(),
            winterPlum(),
            giantPanda(),
            snowPeak(),
            sampan(),
            teaPavilion(),
            longevityPeach(),
            autumnBloom(),
            festivalDrum(),
            frontierWall(),
        )
    }

    // 50 tiles
    private fun paperBoat() = layout(
        "paper_boat", "Paper Boat",
        "A folded paper boat sets sail with its pointed sail held high.",
    ) {
        row(z = 0, y = 0, 8, 10)
        row(z = 0, y = 2, 6, 8, 10)
        row(z = 0, y = 4, 4, 6, 8, 10)
        row(z = 0, y = 6, 2, 4, 6, 8, 10, 12)
        row(z = 0, y = 8, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 10, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 12, 4, 6, 8, 10, 12, 14)
        row(z = 1, y = 3, 9)
        row(z = 1, y = 5, 7, 9)
        row(z = 1, y = 7, 5, 7, 9)
        row(z = 1, y = 9, 5, 7, 9, 11)
        row(z = 2, y = 5, 8)
        row(z = 2, y = 7, 6, 8)
    }

    // 76 tiles
    private fun ancientCoin() = layout(
        "ancient_coin", "Ancient Coin",
        "An ancient round coin is pierced by a square hole for its string.",
    ) {
        row(z = 0, y = 0, 4, 6, 8, 10, 12, 14)
        row(z = 0, y = 2, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 4, 0, 2, 4, 14, 16, 18)
        row(z = 0, y = 6, 0, 2, 4, 14, 16, 18)
        row(z = 0, y = 8, 0, 2, 4, 14, 16, 18)
        row(z = 0, y = 10, 0, 2, 4, 14, 16, 18)
        row(z = 0, y = 12, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 14, 4, 6, 8, 10, 12, 14)
        row(z = 1, y = 1, 7, 9, 11)
        row(z = 1, y = 3, 3, 15)
        row(z = 1, y = 5, 1, 3, 15, 17)
        row(z = 1, y = 7, 1, 3, 15, 17)
        row(z = 1, y = 9, 1, 3, 15, 17)
        row(z = 1, y = 11, 3, 15)
        row(z = 1, y = 13, 7, 9, 11)
        row(z = 2, y = 7, 2, 16)
    }

    // 72 tiles
    private fun paperUmbrella() = layout(
        "paper_umbrella", "Paper Umbrella",
        "An oiled paper umbrella opens wide above its bamboo handle.",
    ) {
        row(z = 0, y = 0, 8, 10)
        row(z = 0, y = 2, 4, 6, 8, 10, 12, 14)
        row(z = 0, y = 4, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 10, 8, 10)
        row(z = 0, y = 12, 8, 10)
        row(z = 0, y = 14, 8, 10)
        row(z = 0, y = 16, 8, 10)
        row(z = 1, y = 1, 9)
        row(z = 1, y = 3, 5, 7, 9, 11, 13)
        row(z = 1, y = 5, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 7, 5, 7, 9, 11, 13)
        row(z = 2, y = 3, 6, 8, 10, 12)
        row(z = 2, y = 5, 6, 8, 10, 12)
        row(z = 3, y = 4, 7, 9)
    }

    // 86 tiles
    private fun winterPlum() = layout(
        "winter_plum", "Winter Plum",
        "A plum blossom opens its petals at the end of winter.",
    ) {
        row(z = 0, y = 0, 8, 10)
        row(z = 0, y = 2, 6, 8, 10, 12)
        row(z = 0, y = 4, 0, 2, 6, 8, 10, 12, 16, 18)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 8, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 10, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 12, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 14, 0, 2, 6, 8, 10, 12, 16, 18)
        row(z = 0, y = 16, 6, 8, 10, 12)
        row(z = 0, y = 18, 8, 10)
        row(z = 1, y = 5, 7, 9, 11)
        row(z = 1, y = 7, 5, 7, 9, 11, 13)
        row(z = 1, y = 9, 5, 7, 9, 11, 13)
        row(z = 1, y = 11, 5, 7, 9, 11, 13)
        row(z = 1, y = 13, 7, 9, 11)
        row(z = 2, y = 9, 10)
    }

    // 80 tiles
    private fun giantPanda() = layout(
        "giant_panda", "Giant Panda",
        "A giant panda peers out with round black ears and a broad, friendly face.",
    ) {
        row(z = 0, y = 0, 2, 4, 14, 16)
        row(z = 0, y = 2, 2, 4, 14, 16)
        row(z = 0, y = 4, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 10, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 12, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 14, 4, 6, 8, 10, 12, 14)
        row(z = 1, y = 5, 7, 9, 11)
        row(z = 1, y = 7, 5, 7, 9, 11, 13)
        row(z = 1, y = 9, 5, 7, 9, 11, 13)
        row(z = 1, y = 11, 7, 9, 11)
        row(z = 2, y = 7, 8, 10)
        row(z = 2, y = 9, 8, 10)
    }


    // 82 tiles
    private fun snowPeak() = layout(
        "snow_peak", "Snow Peak",
        "A lone snow-capped volcano rises in a long, even sweep above the plain.",
        onSide = true,
    ) {
        row(z = 0, y = 0, 14, 16, 18)
        row(z = 0, y = 2, 12, 14, 16, 18, 20)
        row(z = 0, y = 4, 10, 12, 14, 16, 18, 20, 22)
        row(z = 0, y = 6, 8, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 0, y = 8, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30)
        row(z = 0, y = 10, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30)
        row(z = 1, y = 1, 15, 17)
        row(z = 1, y = 3, 13, 15, 17, 19)
        row(z = 1, y = 5, 11, 13, 15, 17, 19, 21)
        row(z = 1, y = 7, 11, 13, 15, 17, 19, 21)
        row(z = 2, y = 1, 16)
        row(z = 2, y = 3, 14, 16, 18)
        row(z = 2, y = 5, 12, 14, 16, 18, 20)
        row(z = 3, y = 4, 15)
    }

    // 88 tiles
    private fun sampan() = layout(
        "sampan", "Sampan",
        "A flat-bottomed sampan drifts on the river under its curved woven canopy.",
        onSide = true,
    ) {
        row(z = 0, y = 0, 12, 14, 16, 18, 20)
        row(z = 0, y = 2, 10, 12, 14, 16, 18, 20, 22)
        row(z = 0, y = 4, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30)
        row(z = 0, y = 6, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30)
        row(z = 0, y = 8, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28)
        row(z = 0, y = 10, 8, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 1, y = 1, 13, 15, 17)
        row(z = 1, y = 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25)
        row(z = 1, y = 7, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25)
        row(z = 2, y = 1, 14)
    }

    // 100 tiles
    private fun teaPavilion() = layout(
        "tea_pavilion", "Tea Pavilion",
        "A tea pavilion stands on its stone platform under a sweeping roof.",
    ) {
        row(z = 0, y = 0, 8, 10)
        row(z = 0, y = 2, 4, 6, 8, 10, 12, 14)
        row(z = 0, y = 4, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 8, 4, 6, 8, 10, 12, 14)
        row(z = 0, y = 10, 4, 6, 12, 14)
        row(z = 0, y = 12, 4, 6, 12, 14)
        row(z = 0, y = 14, 4, 6, 8, 10, 12, 14)
        row(z = 0, y = 16, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 18, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 1, y = 1, 9)
        row(z = 1, y = 3, 5, 7, 9, 11, 13)
        row(z = 1, y = 5, 1, 3, 5, 7, 9, 11, 13, 15, 17)
        row(z = 1, y = 15, 5, 7, 9, 11, 13)
        row(z = 1, y = 17, 1, 3, 5, 7, 9, 11, 13, 15, 17)
        row(z = 2, y = 3, 6, 8, 10)
    }

    // 116 tiles
    private fun longevityPeach() = layout(
        "longevity_peach", "Longevity Peach",
        "The peach of long life hangs ripe beneath a single leaf.",
    ) {
        row(z = 0, y = 0, 10, 12)
        row(z = 0, y = 2, 6, 8, 10, 12)
        row(z = 0, y = 4, 4, 6, 8, 10, 12, 14)
        row(z = 0, y = 6, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 10, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 12, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 14, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 16, 4, 6, 8, 10, 12, 14)
        row(z = 0, y = 18, 6, 8, 10, 12)
        row(z = 0, y = 20, 8, 10)
        row(z = 1, y = 7, 5, 7, 9, 11, 13)
        row(z = 1, y = 9, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 11, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 13, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 15, 5, 7, 9, 11, 13)
        row(z = 2, y = 9, 6, 8, 10, 12)
        row(z = 2, y = 11, 4, 6, 8, 10, 12, 14)
        row(z = 2, y = 13, 6, 8, 10, 12)
        row(z = 3, y = 10, 9)
    }

    // 136 tiles
    private fun autumnBloom() = layout(
        "autumn_bloom", "Autumn Bloom",
        "An autumn chrysanthemum opens in layer upon layer of petals.",
    ) {
        row(z = 0, y = 0, 6, 8, 10, 12)
        row(z = 0, y = 2, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 4, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 10, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 12, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 14, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 16, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 18, 6, 8, 10, 12)
        row(z = 1, y = 3, 5, 7, 9, 11, 13)
        row(z = 1, y = 5, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 7, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 9, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 11, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 13, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 15, 5, 7, 9, 11, 13)
        row(z = 2, y = 7, 6, 8, 10, 12)
        row(z = 2, y = 9, 6, 8, 10, 12)
        row(z = 2, y = 11, 8, 10, 12)
    }

    // 130 tiles
    private fun festivalDrum() = layout(
        "festival_drum", "Festival Drum",
        "A great festival drum rests on its stand, its skin stretched tight.",
    ) {
        row(z = 0, y = 0, 4, 6, 8, 10, 12, 14)
        row(z = 0, y = 2, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 4, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 10, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 12, 4, 6, 8, 10, 12, 14)
        row(z = 0, y = 14, 2, 4, 14, 16)
        row(z = 0, y = 16, 2, 4, 14, 16)
        row(z = 1, y = 1, 5, 7, 9, 11, 13)
        row(z = 1, y = 3, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 5, 1, 3, 5, 7, 9, 11, 13, 15, 17)
        row(z = 1, y = 7, 1, 3, 5, 7, 9, 11, 13, 15, 17)
        row(z = 1, y = 9, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 11, 5, 7, 9, 11, 13)
        row(z = 1, y = 15, 3, 15)
        row(z = 2, y = 3, 6, 8, 10, 12)
        row(z = 2, y = 5, 4, 6, 8, 10, 12, 14)
        row(z = 2, y = 7, 4, 6, 8, 10, 12, 14)
        row(z = 2, y = 9, 6, 8, 10, 12)
    }

    // 144 tiles
    private fun frontierWall() = layout(
        "frontier_wall", "Frontier Wall",
        "The Great Wall winds over the hills, battlements along its top.",
        onSide = true,
    ) {
        row(z = 0, y = 0, 0, 2, 6, 8, 12, 14, 18, 20, 24, 26, 30, 32)
        row(z = 0, y = 2, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 4, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 6, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30)
        row(z = 0, y = 8, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28)
        row(z = 1, y = 1, 1, 7, 13, 19, 25, 31)
        row(z = 1, y = 3, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31)
        row(z = 1, y = 5, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29)
        row(z = 1, y = 7, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27)
        row(z = 2, y = 3, 6, 8, 12, 14, 18, 20, 24, 26, 30)
        row(z = 2, y = 5, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28)
    }

    // [onSide]: a wide board, always played with the phone on its side (as Mah's wide levels)
    private fun layout(id: String, name: String, description: String, onSide: Boolean = false, rows: MutableList<TilePosition>.() -> Unit) =
        Constellation(
            id = id,
            name = name,
            onSide = onSide,
            // Provisional: replaced by the measured label
            difficulty = Difficulty.HARD,
            description = description,
            positions = mutableListOf<TilePosition>().apply(rows),
        )

    private fun MutableList<TilePosition>.row(z: Int, y: Int, vararg xs: Int) {
        for (x in xs) add(TilePosition(x.toFloat(), y.toFloat(), z))
    }
}
