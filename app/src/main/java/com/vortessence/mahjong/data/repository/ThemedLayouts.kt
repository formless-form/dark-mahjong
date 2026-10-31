package com.vortessence.mahjong.data.repository

import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.Difficulty
import com.vortessence.mahjong.data.model.TilePosition

/**
 * Themed layouts (2026-10-31), for Layouts (not the levels): eight wide boards and three to play
 * upright. Each raised layer sits half a tile off the one below, which is why their x and y run
 * odd and even by turn; so stacks always leave tiles in pairs, and every deal works first time.
 * Each row is a layer ([z]), a row of the board ([y]) and the tiles along it ([x]), in the
 * half-tile units of every layout. Their difficulty labels come from measuring them
 * (LayoutDifficulty), like every layout's.
 */
internal object ThemedLayouts {
    val layouts: List<Constellation> by lazy {
        listOf(
            steamTrain(),
            blueWhale(),
            citySkyline(),
            lotusPond(),
            junkShip(),
            redFox(),
            craneInFlight(),
            comet(),
            standingOwl(),
            mountainPass(),
            stoneArch(),
        )
    }

    // 124 tiles
    private fun steamTrain() = layout(
        "steam_train", "Steam Train",
        "A steam locomotive with a tall funnel, a domed boiler and a roomy cab pulls its coal tender.",
    ) {
        row(z = 0, y = 0, 2, 4, 18, 20, 22, 24)
        row(z = 0, y = 2, 2, 4, 10, 12, 18, 20, 22, 24)
        row(z = 0, y = 4, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 28, 30, 32)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 28, 30, 32)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 10, 2, 4, 10, 12, 18, 20, 26, 28)
        row(z = 0, y = 12, 2, 4, 10, 12, 18, 20, 26, 28)
        row(z = 1, y = 1, 3, 19, 21, 23)
        row(z = 1, y = 3, 11, 19, 21, 23)
        row(z = 1, y = 5, 1, 3, 5, 7, 9, 11, 13, 15, 19, 21, 23, 29, 31)
        row(z = 1, y = 7, 1, 3, 5, 7, 9, 11, 13, 15, 19, 21, 23, 29, 31)
        row(z = 1, y = 11, 3, 11, 19, 27)
        row(z = 2, y = 1, 22)
        row(z = 2, y = 3, 20, 22)
        row(z = 2, y = 5, 20, 22)
        row(z = 2, y = 7, 20, 22)
    }

    // 134 tiles
    private fun blueWhale() = layout(
        "blue_whale", "Blue Whale",
        "A great blue whale glides along with its rounded head forward and its tail flukes raised.",
    ) {
        row(z = 0, y = 0, 4, 6, 8, 10, 12, 30, 32)
        row(z = 0, y = 2, 2, 4, 6, 8, 10, 12, 14, 16, 28, 30, 32)
        row(z = 0, y = 4, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 26, 28, 30)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 0, y = 10, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22)
        row(z = 0, y = 12, 6, 8, 10, 12, 14, 16)
        row(z = 1, y = 1, 7, 9, 11, 31)
        row(z = 1, y = 3, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 5, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19)
        row(z = 1, y = 7, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21)
        row(z = 1, y = 9, 3, 5, 7, 9, 11, 13, 15, 17, 19)
        row(z = 1, y = 11, 7, 9, 11, 13, 15)
        row(z = 2, y = 5, 6, 8, 10, 12, 14, 16)
        row(z = 2, y = 7, 6, 8, 10, 12, 14, 16)
    }

    // 104 tiles
    private fun citySkyline() = layout(
        "city_skyline", "City Skyline",
        "A city skyline of towers and spires rises above a busy street.",
    ) {
        row(z = 0, y = 0, 4, 6, 8, 24, 26, 28)
        row(z = 0, y = 2, 4, 6, 8, 14, 16, 18, 24, 26, 28)
        row(z = 0, y = 4, 2, 4, 6, 8, 12, 14, 16, 18, 20, 24, 26, 28, 30)
        row(z = 0, y = 6, 2, 4, 6, 8, 12, 14, 16, 18, 20, 24, 26, 28, 30)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 10, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 1, y = 1, 5, 7, 25, 27)
        row(z = 1, y = 3, 5, 7, 15, 17, 25, 27)
        row(z = 1, y = 5, 3, 5, 7, 13, 15, 17, 19, 25, 27, 29)
        row(z = 2, y = 1, 6, 26)
        row(z = 2, y = 3, 6, 16, 26)
        row(z = 2, y = 5, 6, 14, 16, 26)
    }

    // 122 tiles
    private fun lotusPond() = layout(
        "lotus_pond", "Lotus Pond",
        "A lotus flower opens above a still pond between two floating lily pads.",
    ) {
        row(z = 0, y = 0, 12, 14, 18, 20)
        row(z = 0, y = 2, 8, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 0, y = 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 10, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30)
        row(z = 0, y = 12, 8, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 1, y = 1, 13, 19)
        row(z = 1, y = 3, 9, 11, 13, 15, 17, 19, 21, 23)
        row(z = 1, y = 5, 9, 11, 13, 15, 17, 19, 21, 23)
        row(z = 1, y = 7, 1, 3, 5, 27, 29, 31)
        row(z = 1, y = 9, 3, 5, 27, 29)
        row(z = 2, y = 3, 10, 12, 14, 16, 18, 20, 22)
        row(z = 2, y = 5, 14, 16, 18)
        row(z = 3, y = 4, 15, 17)
    }

    // 94 tiles
    private fun junkShip() = layout(
        "junk_ship", "Junk Ship",
        "A junk ship sails on with two battened sails above its high-sterned hull.",
    ) {
        row(z = 0, y = 0, 16, 18, 20, 22)
        row(z = 0, y = 2, 4, 6, 8, 10, 16, 18, 20, 22, 24)
        row(z = 0, y = 4, 4, 6, 8, 10, 16, 18, 20, 22, 24, 30, 32)
        row(z = 0, y = 6, 0, 2, 6, 8, 10, 12, 18, 20, 22, 24, 30, 32)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 10, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28)
        row(z = 1, y = 1, 17, 19, 21)
        row(z = 1, y = 3, 5, 7, 9, 17, 19, 21, 23)
        row(z = 1, y = 5, 7, 9, 19, 21, 23, 31)
        row(z = 1, y = 7, 31)
        row(z = 2, y = 1, 18, 20)
        row(z = 2, y = 3, 6, 8, 18, 20, 22)
        row(z = 2, y = 5, 8, 20, 22)
        row(z = 3, y = 2, 19)
    }

    // 90 tiles
    private fun redFox() = layout(
        "red_fox", "Red Fox",
        "A red fox trots along with its ears pricked and its bushy tail streaming behind.",
    ) {
        row(z = 0, y = 0, 2, 4, 8, 10)
        row(z = 0, y = 2, 2, 4, 6, 8, 10, 24, 26, 28, 30, 32)
        row(z = 0, y = 4, 0, 2, 4, 6, 8, 10, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30)
        row(z = 0, y = 8, 4, 6, 10, 12, 20, 22, 26, 28)
        row(z = 0, y = 10, 4, 6, 10, 12, 20, 22, 26, 28)
        row(z = 1, y = 1, 3, 9)
        row(z = 1, y = 3, 3, 5, 7, 9, 25, 27, 29, 31)
        row(z = 1, y = 5, 1, 3, 5, 7, 9, 23, 25, 27, 29)
        row(z = 2, y = 3, 4, 6, 8, 26, 28, 30)
        row(z = 2, y = 5, 2, 4, 6, 8, 24, 26, 28)
    }

    // 82 tiles
    private fun craneInFlight() = layout(
        "crane_in_flight", "Crane in Flight",
        "A crane flies with its neck stretched out, wings beating and legs trailing behind.",
    ) {
        row(z = 0, y = 0, 14, 16, 18, 20)
        row(z = 0, y = 2, 14, 16, 18, 20, 22)
        row(z = 0, y = 4, 0, 2, 14, 16, 18, 20, 22, 24)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28)
        row(z = 0, y = 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 10, 10, 12, 14, 16, 18, 26, 28, 30, 32)
        row(z = 0, y = 12, 10, 12, 14, 16)
        row(z = 1, y = 1, 15, 17, 19)
        row(z = 1, y = 3, 15, 17, 19, 21)
        row(z = 1, y = 5, 1)
        row(z = 1, y = 9, 11, 13, 15, 17)
        row(z = 1, y = 11, 11, 13, 15)
        row(z = 2, y = 1, 16, 18)
        row(z = 2, y = 3, 16, 18, 20)
        row(z = 2, y = 9, 12, 14, 16)
        row(z = 2, y = 11, 12, 14)
    }

    // 126 tiles
    private fun comet() = layout(
        "comet", "Comet",
        "A blazing comet sweeps across the sky trailing three broad streams of light.",
    ) {
        row(z = 0, y = 0, 0, 2, 24, 26, 28)
        row(z = 0, y = 2, 0, 2, 4, 6, 8, 20, 22, 24, 26, 28, 30)
        row(z = 0, y = 4, 2, 4, 6, 8, 10, 12, 14, 16, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 6, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 8, 2, 4, 6, 8, 10, 12, 14, 16, 20, 22, 24, 26, 28, 30, 32)
        row(z = 0, y = 10, 0, 2, 4, 6, 8, 20, 22, 24, 26, 28, 30)
        row(z = 0, y = 12, 0, 2, 24, 26, 28)
        row(z = 1, y = 1, 1, 25, 27)
        row(z = 1, y = 3, 3, 5, 7, 23, 25, 27, 29)
        row(z = 1, y = 5, 21, 23, 25, 27, 29, 31)
        row(z = 1, y = 7, 21, 23, 25, 27, 29, 31)
        row(z = 1, y = 9, 3, 5, 7, 23, 25, 27, 29)
        row(z = 1, y = 11, 1, 25, 27)
        row(z = 2, y = 3, 24, 26, 28)
        row(z = 2, y = 5, 22, 24, 26, 28, 30)
        row(z = 2, y = 7, 22, 24, 26, 28, 30)
        row(z = 2, y = 9, 24, 26, 28)
        row(z = 3, y = 6, 27)
    }

    // 126 tiles
    private fun standingOwl() = layout(
        "standing_owl", "Standing Owl",
        "A wise owl stands on its branch with ear tufts raised and wide, round eyes.",
    ) {
        row(z = 0, y = 0, 0, 2, 14, 16)
        row(z = 0, y = 2, 0, 2, 4, 12, 14, 16)
        row(z = 0, y = 4, 0, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 10, 2, 4, 6, 8, 10, 12, 14)
        row(z = 0, y = 12, 0, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 14, 0, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 16, 0, 2, 4, 6, 8, 10, 12, 14, 16)
        row(z = 0, y = 18, 2, 4, 6, 8, 10, 12, 14)
        row(z = 0, y = 20, 4, 6, 10, 12)
        row(z = 1, y = 1, 1, 15)
        row(z = 1, y = 5, 1, 3, 13, 15)
        row(z = 1, y = 7, 1, 3, 5, 7, 9, 11, 13, 15)
        row(z = 1, y = 9, 5, 7, 9, 11)
        row(z = 1, y = 11, 5, 7, 9, 11)
        row(z = 1, y = 13, 5, 7, 9, 11)
        row(z = 1, y = 15, 5, 7, 9, 11)
        row(z = 2, y = 5, 14)
        row(z = 2, y = 7, 2, 4, 12, 14)
        row(z = 2, y = 9, 6, 8, 10)
        row(z = 2, y = 11, 6, 8, 10)
        row(z = 2, y = 13, 6, 8, 10)
    }

    // 128 tiles
    private fun mountainPass() = layout(
        "mountain_pass", "Mountain Pass",
        "Two rugged peaks stand either side of a high pass above broad foothills.",
    ) {
        row(z = 0, y = 0, 6, 8)
        row(z = 0, y = 2, 4, 6, 8, 10, 18, 20)
        row(z = 0, y = 4, 2, 4, 6, 8, 10, 12, 16, 18, 20, 22)
        row(z = 0, y = 6, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 0, y = 8, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 0, y = 10, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 0, y = 12, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24)
        row(z = 0, y = 14, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22)
        row(z = 1, y = 1, 7)
        row(z = 1, y = 3, 5, 7, 9, 19)
        row(z = 1, y = 5, 3, 5, 7, 9, 11, 17, 19, 21)
        row(z = 1, y = 7, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23)
        row(z = 2, y = 3, 6, 8)
        row(z = 2, y = 5, 4, 6, 8, 10, 18, 20)
        row(z = 2, y = 7, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22)
        row(z = 3, y = 4, 7)
        row(z = 3, y = 6, 5, 7)
    }

    // 100 tiles
    private fun stoneArch() = layout(
        "stone_arch", "Stone Arch",
        "A stone arch spans an open passage on two sturdy piers.",
    ) {
        row(z = 0, y = 0, 6, 8, 10, 12, 14, 16, 18)
        row(z = 0, y = 2, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22)
        row(z = 0, y = 4, 0, 2, 4, 6, 18, 20, 22, 24)
        row(z = 0, y = 6, 0, 2, 4, 20, 22, 24)
        row(z = 0, y = 8, 0, 2, 4, 20, 22, 24)
        row(z = 0, y = 10, 0, 2, 4, 20, 22, 24)
        row(z = 0, y = 12, 0, 2, 4, 20, 22, 24)
        row(z = 0, y = 14, 0, 2, 4, 6, 18, 20, 22, 24)
        row(z = 0, y = 16, 0, 2, 4, 6, 18, 20, 22, 24)
        row(z = 1, y = 1, 9, 11, 13)
        row(z = 1, y = 5, 1, 3, 21, 23)
        row(z = 1, y = 7, 1, 3, 21, 23)
        row(z = 1, y = 9, 1, 3, 21, 23)
        row(z = 1, y = 11, 1, 3, 21, 23)
        row(z = 1, y = 13, 1, 3, 21, 23)
        row(z = 1, y = 15, 1, 3, 21, 23)
        row(z = 2, y = 1, 12)
        row(z = 2, y = 5, 2, 22)
        row(z = 2, y = 7, 2, 22)
        row(z = 2, y = 9, 2, 22)
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
