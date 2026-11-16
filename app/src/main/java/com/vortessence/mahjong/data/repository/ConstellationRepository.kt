package com.vortessence.mahjong.data.repository

import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.Difficulty
import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TilePosition
import com.vortessence.mahjong.data.model.TileSuit

/** "Level 3", "Tutorial", or null for a board that isn't a level. */
fun levelLabel(levelIndex: Int?): String? = when (levelIndex) {
    null -> null
    ConstellationRepository.TUTORIAL_LEVEL -> "Tutorial"
    else -> "Level $levelIndex"
}

object ConstellationRepository {

    private fun pos(x: Number, y: Number, z: Int) = TilePosition(x.toFloat(), y.toFloat(), z)

    const val TUTORIAL_ID = "tutorial"
    const val TUTORIAL_LEVEL = 0

    /** Level 0 in both phone and tablet mode. */
    val tutorialConstellation: Constellation by lazy { createTutorial() }

    /** Builds and sorts the catalog in the background at launch, so the first screens don't wait. */
    fun prepareAsync() {
        Thread({
            constellations
            levelConstellations
        }, "layouts").apply { isDaemon = true }.start()
    }

    /** Upright phone layouts, so boards don't zoom out too far. */
    private val phoneConstellations: List<Constellation> by lazy {
        listOf(
            createJadePillar(),
            createBambooSpire(),
            createDragonSaber(),
            createCelestialLantern(),
            createToriiArch(),
            createCeladonHourglass(),
            createCascadingFalls(),
            createPhoenixTower(),
            createImperialMonolith(),
            createRuyiScepter(),
            createPillarOfHeaven(),
            createGrandPagodaSpire(),
        ).map(::measured)
    }

    /** Kept for tablets and manual selection. */
    val classicConstellations: List<Constellation> by lazy {
        listOf(
            createMiniTurtle(),
            createCross(),
            createDiamond(),
            createPyramid(),
            createHeart(),
            createCrab(),
            createButterfly(),
            createClassicTurtle(),
            createFortress(),
            createCastle(),
            createDragon(),
            createPagoda(),
        ).map(::measured)
    }

    /** From Mah by ffalt (MIT): the ones that suit a phone held upright. */
    val mahConstellations: List<Constellation> by lazy {
        MahLayouts.boards.map { measured(it.toConstellation()) }
    }

    /** Layouts only, played on the phone's side. */
    val wideConstellations: List<Constellation> by lazy {
        WideLayouts.layouts.map(::measured)
    }

    /** Levels 89-100 when they joined, slotted in by difficulty. */
    val journeyConstellations: List<Constellation> by lazy {
        JourneyLayouts.layouts.map(::measured)
    }

    /** Layouts only. */
    val themedConstellations: List<Constellation> by lazy {
        ThemedLayouts.layouts.map(::measured)
    }

    // Labelled by measured clear rate (LayoutDifficulty), not the label it was made with
    private fun measured(c: Constellation): Constellation =
        LayoutDifficulty.clearRate[c.id]?.let { c.copy(difficulty = LayoutDifficulty.label(it)) } ?: c

    /**
     * Phone, Mah and Journey layouts, easy to hard. Levels 1-8 are unchanged since before Mah's
     * joined (they measured easiest too).
     */
    val levelConstellations: List<Constellation> by lazy {
        (phoneConstellations + mahConstellations + journeyConstellations).sortedWith(easyToHard)
    }

    /** The tutorial, then everything else easy to hard. */
    val constellations: List<Constellation> by lazy {
        listOf(tutorialConstellation) +
            (phoneConstellations + classicConstellations + wideConstellations + themedConstellations + mahConstellations + journeyConstellations)
                .sortedWith(easyToHard)
    }

    /** Highest clear rate first (LayoutDifficulty), then fewer tiles, then name; unmeasured by label. */
    private val easyToHard: Comparator<Constellation> =
        compareByDescending<Constellation> { LayoutDifficulty.clearRate[it.id] ?: unmeasured(it) }
            .thenBy { it.tileCount }
            .thenBy { it.name }

    private fun unmeasured(c: Constellation): Float = when (c.difficulty) {
        Difficulty.EASY -> 0.90f
        Difficulty.MEDIUM -> 0.60f
        Difficulty.HARD -> 0.35f
        Difficulty.EXPERT -> 0.10f
    }

    /** Levels-mode number (0 for the tutorial), or null if not a level. */
    fun levelIndexOf(id: String): Int? =
        if (id == TUTORIAL_ID) TUTORIAL_LEVEL
        else levelConstellations.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1)

    /** Null if none (e.g. a save from a removed layout). */
    fun getById(id: String): Constellation? = constellations.firstOrNull { it.id == id }

    fun getLevel(levelIndex: Int): Constellation {
        if (levelIndex == TUTORIAL_LEVEL) return tutorialConstellation
        val levels = levelConstellations
        val index = (levelIndex - 1).coerceIn(0, levels.size - 1)
        return levels[index]
    }

    val totalLevels: Int
        get() = levelConstellations.size

    // 14 tiles. Tile order defines TutorialScript's tile ids:
    //   row y=0: 0..3, row y=2: 4..7, row y=4: 8..11, top layer: 12 (on 5), 13 (on 6)
    private fun createTutorial(): Constellation {
        val list = mutableListOf<TilePosition>()
        for (y in listOf(0, 2, 4)) {
            for (x in listOf(0, 2, 4, 6)) list.add(pos(x, y, 0))
        }
        list.add(pos(2, 2, 1))
        list.add(pos(4, 2, 1))

        val bamboo5 = Tile(TileSuit.BAMBOO, 5)
        val redDragon = Tile(TileSuit.DRAGON, Tile.DRAGON_RED)
        val eastWind = Tile(TileSuit.WIND, Tile.WIND_EAST)
        val characters3 = Tile(TileSuit.CHARACTER, 3)
        val circles7 = Tile(TileSuit.CIRCLE, 7)
        // Bottom two rows mirror each other so free play can't get stuck
        val tiles = listOf(
            bamboo5, Tile(TileSuit.SEASON, Tile.SEASON_SPRING), Tile(TileSuit.SEASON, Tile.SEASON_AUTUMN), bamboo5,
            redDragon, characters3, circles7, eastWind,
            eastWind, circles7, characters3, redDragon,
            Tile(TileSuit.FLOWER, Tile.FLOWER_PLUM), Tile(TileSuit.FLOWER, Tile.FLOWER_CHRYSANTHEMUM),
        )

        return Constellation(
            id = TUTORIAL_ID,
            name = "Tutorial",
            difficulty = Difficulty.EASY,
            description = "Learn which tiles can be selected and which tiles match, step by step.",
            positions = list,
            fixedTiles = tiles,
        )
    }

    // 36 tiles
    private fun createJadePillar(): Constellation {
        val list = mutableListOf<TilePosition>()
        val rowsL0 = listOf(
            0 to listOf(4, 6),
            2 to listOf(2, 4, 6, 8),
            4 to listOf(4, 6),
            6 to listOf(2, 4, 6, 8),
            8 to listOf(4, 6),
            10 to listOf(2, 4, 6, 8),
            12 to listOf(4, 6),
            14 to listOf(2, 4, 6, 8),
            16 to listOf(4, 6),
        )
        for ((y, xs) in rowsL0) {
            for (x in xs) list.add(pos(x, y, 0))
        }

        // Shaft center ridge
        for (y in listOf(4, 6, 8, 10)) {
            list.add(pos(4, y, 1))
            list.add(pos(6, y, 1))
        }

        // Gem crown
        list.add(pos(5, 6, 2))
        list.add(pos(5, 8, 2))

        return Constellation(
            id = "jade_pillar",
            name = "Jade Pillar",
            difficulty = Difficulty.EASY,
            description = "A slender sacred jade obelisk with a tiered capital and glowing gem crest.",
            positions = list,
        )
    }

    // 48 tiles
    private fun createBambooSpire(): Constellation {
        val list = mutableListOf<TilePosition>()
        val rowsL0 = listOf(
            0 to listOf(2, 4, 6),
            2 to listOf(2, 4, 6),
            4 to listOf(0, 2, 4, 6, 8),
            6 to listOf(2, 4, 6),
            8 to listOf(0, 2, 4, 6, 8),
            10 to listOf(2, 4, 6),
            12 to listOf(0, 2, 4, 6, 8),
            14 to listOf(2, 4, 6),
            16 to listOf(0, 2, 4, 6, 8),
            18 to listOf(4),
        )
        for ((y, xs) in rowsL0) {
            for (x in xs) list.add(pos(x, y, 0))
        }

        // Node ridges
        for (y in listOf(4, 8, 12)) {
            list.add(pos(2, y, 1))
            list.add(pos(4, y, 1))
            list.add(pos(6, y, 1))
        }
        list.add(pos(4, 6, 1))

        // Heart node
        list.add(pos(4, 6, 2))
        list.add(pos(4, 8, 2))

        return Constellation(
            id = "bamboo_spire",
            name = "Bamboo Spire",
            difficulty = Difficulty.EASY,
            description = "A soaring vertical bamboo stalk with stepped nodes and angled side shoots.",
            positions = list
        )
    }

    // 56 tiles
    private fun createDragonSaber(): Constellation {
        val list = mutableListOf<TilePosition>()
        val rowsL0 = listOf(
            0 to listOf(4),
            2 to listOf(2, 4, 6),
            4 to listOf(2, 4, 6),
            6 to listOf(0, 2, 4, 6, 8),
            8 to listOf(2, 4, 6),
            10 to listOf(0, 2, 4, 6, 8),
            12 to listOf(2, 4, 6),
            14 to listOf(0, 2, 4, 6, 8),
            16 to listOf(0, 2, 4, 6, 8),
            18 to listOf(2, 4, 6),
            20 to listOf(0, 2, 4, 6, 8),
            22 to listOf(4),
        )
        for ((y, xs) in rowsL0) {
            for (x in xs) list.add(pos(x, y, 0))
        }

        // Fuller spine, crossguard & pommel
        for (y in listOf(4, 6, 8, 10, 12)) {
            list.add(pos(4, y, 1))
        }
        list.add(pos(2, 14, 1))
        list.add(pos(4, 14, 1))
        list.add(pos(6, 14, 1))
        list.add(pos(4, 16, 1))
        list.add(pos(4, 18, 1))
        list.add(pos(2, 20, 1))
        list.add(pos(6, 20, 1))

        // Guard dragon eye & spine jewel
        list.add(pos(4, 14, 2))
        list.add(pos(4, 8, 2))

        return Constellation(
            id = "dragon_saber",
            name = "Dragon Saber",
            difficulty = Difficulty.EASY,
            description = "A double-edged ceremonial blade featuring an ornamental guard, grip, and pommel.",
            positions = list
        )
    }

    // 64 tiles
    private fun createCelestialLantern(): Constellation {
        val list = mutableListOf<TilePosition>()
        val rowsL0 = listOf(
            0 to listOf(4, 6),
            2 to listOf(4, 6),
            4 to listOf(2, 4, 6, 8),
            6 to listOf(0, 2, 4, 6, 8, 10),
            8 to listOf(0, 2, 4, 6, 8, 10),
            10 to listOf(0, 2, 4, 6, 8, 10),
            12 to listOf(0, 2, 4, 6, 8, 10),
            14 to listOf(2, 4, 6, 8),
            16 to listOf(4, 6),
            18 to listOf(2, 4, 6, 8),
            20 to listOf(0, 2, 4, 6, 8, 10),
        )
        for ((y, xs) in rowsL0) {
            for (x in xs) list.add(pos(x, y, 0))
        }

        // Luminous core window
        for (y in listOf(8, 10, 12)) {
            for (x in listOf(2, 4, 6, 8)) {
                list.add(pos(x, y, 1))
            }
        }

        // Central medallion
        list.add(pos(4, 9, 2))
        list.add(pos(6, 9, 2))
        list.add(pos(4, 11, 2))
        list.add(pos(6, 11, 2))

        return Constellation(
            id = "celestial_lantern",
            name = "Celestial Lantern",
            difficulty = Difficulty.EASY,
            description = "A traditional hanging lantern with suspension cord, luminous chamber, and flowing tassel.",
            positions = list
        )
    }

    // 72 tiles
    private fun createToriiArch(): Constellation {
        val list = mutableListOf<TilePosition>()
        // Kasagi
        for (x in listOf(0, 2, 4, 6, 8, 10)) list.add(pos(x, 0, 0))
        // Shimaki
        for (x in listOf(2, 4, 6, 8)) list.add(pos(x, 2, 0))
        // Nuki
        for (x in listOf(0, 2, 4, 6, 8, 10)) list.add(pos(x, 4, 0))
        // Center plaque
        list.add(pos(4, 6, 0))
        list.add(pos(6, 6, 0))
        list.add(pos(4, 8, 0))
        list.add(pos(6, 8, 0))
        // Twin pillars
        for (y in listOf(6, 8, 10, 12, 14, 16)) {
            list.add(pos(0, y, 0))
            list.add(pos(2, y, 0))
            list.add(pos(8, y, 0))
            list.add(pos(10, y, 0))
        }
        // Pillar plinths
        list.add(pos(0, 18, 0))
        list.add(pos(2, 18, 0))
        list.add(pos(8, 18, 0))
        list.add(pos(10, 18, 0))
        // Courtyard foundation
        for (x in listOf(0, 2, 4, 6, 8, 10)) list.add(pos(x, 20, 0))

        // Upper beam crest, pillar ridges & tablet
        for (x in listOf(2, 4, 6, 8)) list.add(pos(x, 0, 1))
        for (y in listOf(8, 10, 12, 14)) {
            list.add(pos(1, y, 1))
            list.add(pos(9, y, 1))
        }
        list.add(pos(4, 6, 1))
        list.add(pos(6, 6, 1))

        // Roof crown & shrine mirror
        list.add(pos(4, 0, 2))
        list.add(pos(6, 0, 2))
        list.add(pos(4, 6, 2))
        list.add(pos(6, 6, 2))

        return Constellation(
            id = "torii_arch",
            name = "Torii Arch",
            difficulty = Difficulty.MEDIUM,
            description = "A grand ceremonial gateway flanked by towering pillars and a sacred crossbeam.",
            positions = list
        )
    }

    // 80 tiles
    private fun createCeladonHourglass(): Constellation {
        val list = mutableListOf<TilePosition>()
        val rowsL0 = listOf(
            0 to listOf(0, 2, 4, 6, 8, 10),
            2 to listOf(2, 4, 6, 8),
            4 to listOf(0, 2, 4, 6, 8, 10),
            6 to listOf(0, 2, 4, 6, 8, 10),
            8 to listOf(2, 4, 6, 8),
            10 to listOf(4, 6),
            12 to listOf(4, 6),
            14 to listOf(2, 4, 6, 8),
            16 to listOf(0, 2, 4, 6, 8, 10),
            18 to listOf(0, 2, 4, 6, 8, 10),
            20 to listOf(2, 4, 6, 8),
            22 to listOf(0, 2, 4, 6, 8, 10),
        )
        for ((y, xs) in rowsL0) {
            for (x in xs) list.add(pos(x, y, 0))
        }

        // Upper bulb, waist collar, lower bulb
        for (y in listOf(4, 6)) {
            for (x in listOf(2, 4, 6, 8)) list.add(pos(x, y, 1))
        }
        for (y in listOf(10, 12)) {
            for (x in listOf(4, 6)) list.add(pos(x, y, 1))
        }
        for (y in listOf(16, 18)) {
            for (x in listOf(2, 4, 6, 8)) list.add(pos(x, y, 1))
        }

        // Chamber emblems
        list.add(pos(4, 5, 2))
        list.add(pos(6, 5, 2))
        list.add(pos(4, 17, 2))
        list.add(pos(6, 17, 2))

        return Constellation(
            id = "celadon_hourglass",
            name = "Celadon Hourglass",
            difficulty = Difficulty.MEDIUM,
            description = "A harmonious vessel form with flared rim, slender waist, and twin tiered chambers.",
            positions = list
        )
    }

    // 96 tiles
    private fun createCascadingFalls(): Constellation {
        val list = mutableListOf<TilePosition>()
        for (y in (0..22) step 2) {
            for (x in listOf(0, 2, 4, 6, 8, 10)) {
                list.add(pos(x, y, 0))
            }
        }

        // Left & right stepped canyon terraces
        for (y in listOf(2, 4, 8, 10)) {
            list.add(pos(0, y, 1))
            list.add(pos(2, y, 1))
        }
        list.add(pos(0, 14, 1))
        list.add(pos(0, 16, 1))

        for (y in listOf(4, 6, 10, 12)) {
            list.add(pos(8, y, 1))
            list.add(pos(10, y, 1))
        }
        list.add(pos(10, 16, 1))
        list.add(pos(10, 18, 1))

        // High canyon promontories
        list.add(pos(1, 3, 2))
        list.add(pos(1, 9, 2))
        list.add(pos(9, 5, 2))
        list.add(pos(9, 11, 2))

        return Constellation(
            id = "cascading_falls",
            name = "Cascading Falls",
            difficulty = Difficulty.MEDIUM,
            description = "A deep mountain chasm with flanking stepped cliffs framing a central waterfall.",
            positions = list
        )
    }

    // 108 tiles
    private fun createPhoenixTower(): Constellation {
        val list = mutableListOf<TilePosition>()
        for (x in listOf(2, 4, 6, 8, 10)) {
            list.add(pos(x, 0, 0))
            list.add(pos(x, 2, 0))
        }
        for (y in (4..20) step 2) {
            for (x in listOf(0, 2, 4, 6, 8, 10, 12)) {
                list.add(pos(x, y, 0))
            }
        }
        for (x in listOf(4, 6, 8)) {
            list.add(pos(x, 22, 0))
        }

        // Center keep & outer wing bastions
        for (y in (4..14) step 2) {
            for (x in listOf(4, 6, 8)) {
                list.add(pos(x, y, 1))
            }
        }
        for (y in listOf(8, 10, 12)) {
            list.add(pos(0, y, 1))
            list.add(pos(12, y, 1))
        }

        // High spire sanctum & wing turrets
        for (y in listOf(6, 8, 10, 12)) {
            list.add(pos(6, y, 2))
        }
        list.add(pos(0, 10, 2))
        list.add(pos(12, 10, 2))
        list.add(pos(4, 8, 2))
        list.add(pos(8, 8, 2))

        return Constellation(
            id = "phoenix_tower",
            name = "Phoenix Tower",
            difficulty = Difficulty.MEDIUM,
            description = "A soaring citadel flanked by vertical wing buttresses and a fortified keep.",
            positions = list
        )
    }

    // 120 tiles
    private fun createImperialMonolith(): Constellation {
        val list = mutableListOf<TilePosition>()
        for (y in (0..22) step 2) {
            for (x in listOf(0, 2, 4, 6, 8, 10)) {
                list.add(pos(x, y, 0))
            }
        }

        for (y in (2..18) step 2) {
            for (x in listOf(2, 4, 6, 8)) {
                list.add(pos(x, y, 1))
            }
        }

        // Stele central spine & terminal caps
        for (y in listOf(6, 8, 10, 12)) {
            list.add(pos(4, y, 2))
            list.add(pos(6, y, 2))
        }
        list.add(pos(4, 4, 2))
        list.add(pos(6, 14, 2))

        // Summit crown
        list.add(pos(5, 7, 3))
        list.add(pos(5, 9, 3))

        return Constellation(
            id = "imperial_monolith",
            name = "Imperial Monolith",
            difficulty = Difficulty.HARD,
            description = "A monumental stone stele enclosed within concentric tiered defensive terraces.",
            positions = list
        )
    }

    // 132 tiles
    private fun createRuyiScepter(): Constellation {
        val list = mutableListOf<TilePosition>()
        for (y in (0..22) step 2) {
            for (x in listOf(0, 2, 4, 6, 8, 10, 12)) {
                list.add(pos(x, y, 0))
            }
        }

        // Cloud head, handle curve & finial
        for (y in listOf(0, 2, 4)) {
            for (x in listOf(2, 4, 6, 8, 10)) list.add(pos(x, y, 1))
        }
        for (y in listOf(6, 8)) {
            for (x in listOf(4, 6, 8)) list.add(pos(x, y, 1))
        }
        for (y in listOf(10, 12)) {
            for (x in listOf(2, 4, 6)) list.add(pos(x, y, 1))
        }
        for (y in listOf(14, 16)) {
            for (x in listOf(4, 6, 8)) list.add(pos(x, y, 1))
        }
        for (x in listOf(4, 6, 8)) list.add(pos(x, 18, 1))

        // Cloud boss & spine ridge
        for (y in listOf(2, 4)) {
            for (x in listOf(4, 6, 8)) list.add(pos(x, y, 2))
        }
        for (y in listOf(8, 10, 12, 14)) {
            list.add(pos(5, y, 2))
        }

        // Dragon pearl gems
        list.add(pos(6, 3, 3))
        list.add(pos(5, 9, 3))

        return Constellation(
            id = "ruyi_scepter",
            name = "Ruyi Scepter",
            difficulty = Difficulty.HARD,
            description = "An auspicious curved jade scepter carved with cloud crests and dragon pearls.",
            positions = list
        )
    }

    // 140 tiles
    private fun createPillarOfHeaven(): Constellation {
        val list = mutableListOf<TilePosition>()
        for (y in (0..22) step 2) {
            for (x in listOf(0, 2, 4, 6, 8, 10, 12)) {
                list.add(pos(x, y, 0))
            }
        }

        // Inner block + side cloud wings
        for (y in (2..16) step 2) {
            for (x in listOf(2, 4, 6, 8, 10)) {
                list.add(pos(x, y, 1))
            }
        }
        list.add(pos(0, 8, 1))
        list.add(pos(12, 8, 1))

        // Central sanctum
        for (y in listOf(6, 8, 10, 12)) {
            for (x in listOf(4, 6, 8)) {
                list.add(pos(x, y, 2))
            }
        }

        // Apex celestial crown
        list.add(pos(6, 8, 3))
        list.add(pos(6, 10, 3))

        return Constellation(
            id = "pillar_of_heaven",
            name = "Pillar of Heaven",
            difficulty = Difficulty.HARD,
            description = "A titanic column anchoring earth and heavens with multi-tiered cloud ramparts.",
            positions = list
        )
    }

    // 144 tiles
    private fun createGrandPagodaSpire(): Constellation {
        val list = mutableListOf<TilePosition>()
        for (x in listOf(2, 4, 6, 8, 10)) {
            list.add(pos(x, 0, 0))
            list.add(pos(x, 2, 0))
        }
        for (y in (4..20) step 2) {
            for (x in listOf(0, 2, 4, 6, 8, 10, 12)) {
                list.add(pos(x, y, 0))
            }
        }
        for (x in listOf(2, 4, 6, 8, 10)) {
            list.add(pos(x, 22, 0))
        }

        for (y in (4..16) step 2) {
            for (x in listOf(2, 4, 6, 8, 10)) {
                list.add(pos(x, y, 1))
            }
        }
        list.add(pos(0, 10, 1))
        list.add(pos(12, 10, 1))
        list.add(pos(6, 2, 1))

        for (y in (6..16) step 2) {
            for (x in listOf(4, 6, 8)) {
                list.add(pos(x, y, 2))
            }
        }

        for (y in listOf(7, 9, 11)) {
            list.add(pos(4, y, 3))
            list.add(pos(6, y, 3))
        }

        list.add(pos(5, 7, 4))
        list.add(pos(5, 9, 4))
        list.add(pos(5, 11, 4))

        // Golden finial
        list.add(pos(5, 9, 5))

        return Constellation(
            id = "pagoda_spire",
            name = "Grand Pagoda Spire",
            difficulty = Difficulty.EXPERT,
            description = "A soaring six-tier vertical pagoda temple reaching 144 tiles, tailored for mobile mastery.",
            positions = list
        )
    }

    // 36 tiles
    private fun createMiniTurtle(): Constellation {
        val list = mutableListOf<TilePosition>()
        for (gx in 0 until 6) {
            for (gy in 0 until 4) {
                list.add(pos(2 + gx * 2, 2 + gy * 2, 0))
            }
        }
        for (gx in 0 until 4) {
            for (gy in 0 until 2) {
                list.add(pos(4 + gx * 2, 4 + gy * 2, 1))
            }
        }
        list.add(pos(6, 5, 2))
        list.add(pos(8, 5, 2))
        // Outer wings
        list.add(pos(0, 5, 0))
        list.add(pos(14, 5, 0))

        return Constellation(
            id = "mini_turtle",
            name = "Mini Turtle",
            difficulty = Difficulty.EASY,
            description = "A friendly compact constellation with accessible paths, perfect for quick games.",
            positions = list
        )
    }

    // 48 tiles
    private fun createCross(): Constellation {
        val list = mutableListOf<TilePosition>()
        // Central 4x4 block
        for (x in listOf(8, 10, 12, 14)) {
            for (y in listOf(4, 6, 8, 10)) {
                list.add(pos(x, y, 0))
            }
        }
        // Left arm
        for (x in listOf(2, 4, 6)) {
            for (y in listOf(6, 8)) {
                list.add(pos(x, y, 0))
            }
        }
        // Right arm
        for (x in listOf(16, 18, 20)) {
            for (y in listOf(6, 8)) {
                list.add(pos(x, y, 0))
            }
        }
        // Top arm
        for (x in listOf(10, 12)) {
            for (y in listOf(0, 2)) {
                list.add(pos(x, y, 0))
            }
        }
        // Bottom arm
        for (x in listOf(10, 12)) {
            for (y in listOf(12, 14)) {
                list.add(pos(x, y, 0))
            }
        }

        // Center
        for (x in listOf(10, 12)) {
            for (y in listOf(6, 8)) {
                list.add(pos(x, y, 1))
            }
        }
        // Arms
        list.add(pos(6, 7, 1))
        list.add(pos(8, 7, 1))
        list.add(pos(14, 7, 1))
        list.add(pos(16, 7, 1))
        list.add(pos(11, 2, 1))
        list.add(pos(11, 4, 1))
        list.add(pos(11, 10, 1))
        list.add(pos(11, 12, 1))

        return Constellation(
            id = "cross",
            name = "Four Winds Cross",
            difficulty = Difficulty.EASY,
            description = "A symmetrical cross layout honoring the four cardinal winds.",
            positions = list
        )
    }

    // 58 tiles
    private fun createDiamond(): Constellation {
        val list = mutableListOf<TilePosition>()
        val rows = listOf(
            0 to listOf(10),
            2 to listOf(8, 10, 12),
            4 to listOf(6, 8, 10, 12, 14),
            6 to listOf(4, 6, 8, 10, 12, 14, 16),
            8 to listOf(2, 4, 6, 8, 10, 12, 14, 16, 18),
            10 to listOf(4, 6, 8, 10, 12, 14, 16),
            12 to listOf(6, 8, 10, 12, 14),
            14 to listOf(8, 10, 12),
            16 to listOf(10)
        )
        for ((y, xs) in rows) {
            for (x in xs) {
                list.add(pos(x, y, 0))
            }
        }
        val rowsL1 = listOf(
            4 to listOf(10),
            6 to listOf(8, 10, 12),
            8 to listOf(6, 8, 10, 12, 14),
            10 to listOf(8, 10, 12),
            12 to listOf(10)
        )
        for ((y, xs) in rowsL1) {
            for (x in xs) {
                list.add(pos(x, y, 1))
            }
        }
        list.add(pos(9, 7, 2))
        list.add(pos(11, 7, 2))
        list.add(pos(9, 9, 2))
        list.add(pos(11, 9, 2))

        return Constellation(
            id = "diamond",
            name = "Imperial Diamond",
            difficulty = Difficulty.EASY,
            description = "An exquisite diamond formation with stepped elevations leading to a central summit.",
            positions = list
        )
    }

    // 64 tiles
    private fun createPyramid(): Constellation {
        val list = mutableListOf<TilePosition>()
        for (x in 2..12 step 2) {
            for (y in 2..12 step 2) {
                list.add(pos(x, y, 0))
            }
        }
        for (x in 4..10 step 2) {
            for (y in 4..10 step 2) {
                list.add(pos(x, y, 1))
            }
        }
        for (x in 6..8 step 2) {
            for (y in 6..8 step 2) {
                list.add(pos(x, y, 2))
            }
        }
        // Side accents
        list.add(pos(4, 7, 2))
        list.add(pos(10, 7, 2))
        list.add(pos(7, 4, 2))
        list.add(pos(7, 10, 2))

        list.add(pos(6, 6, 3))
        list.add(pos(8, 6, 3))
        list.add(pos(6, 8, 3))
        list.add(pos(8, 8, 3))

        return Constellation(
            id = "pyramid",
            name = "Terraced Pyramid",
            difficulty = Difficulty.EASY,
            description = "A classical tiered pyramid with concentric stepped terraces.",
            positions = list
        )
    }

    // 72 tiles
    private fun createHeart(): Constellation {
        val list = mutableListOf<TilePosition>()
        val heartRowMap = listOf(
            0 to listOf(4, 6, 12, 14),
            2 to listOf(2, 4, 6, 8, 10, 12, 14, 16),
            4 to listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18),
            6 to listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18),
            8 to listOf(2, 4, 6, 8, 10, 12, 14, 16),
            10 to listOf(4, 6, 8, 10, 12, 14),
            12 to listOf(6, 8, 10, 12),
            14 to listOf(8, 10)
        )
        for ((y, xs) in heartRowMap) {
            for (x in xs) {
                list.add(pos(x, y, 0))
            }
        }
        val heartL1 = listOf(
            3 to listOf(4, 6, 12, 14),
            5 to listOf(4, 6, 8, 10, 12, 14),
            7 to listOf(6, 8, 10, 12),
            9 to listOf(8, 10)
        )
        for ((y, xs) in heartL1) {
            for (x in xs) {
                list.add(pos(x, y, 1))
            }
        }
        list.add(pos(8, 6, 2))
        list.add(pos(10, 6, 2))
        list.add(pos(8, 8, 2))
        list.add(pos(10, 8, 2))

        return Constellation(
            id = "heart",
            name = "Jade Heart",
            difficulty = Difficulty.MEDIUM,
            description = "A harmonious heart silhouette symbolizing longevity and fortune.",
            positions = list
        )
    }

    // 84 tiles
    private fun createCrab(): Constellation {
        val list = mutableListOf<TilePosition>()
        // Body
        for (x in 6..16 step 2) {
            for (y in 4..10 step 2) {
                list.add(pos(x, y, 0))
            }
        }
        // Claws
        val leftClaw = listOf(
            pos(4, 2, 0), pos(2, 2, 0), pos(0, 2, 0), pos(2, 0, 0),
            pos(4, 0, 0), pos(0, 4, 0), pos(2, 4, 0), pos(4, 4, 0)
        )
        val rightClaw = listOf(
            pos(18, 2, 0), pos(20, 2, 0), pos(22, 2, 0), pos(20, 0, 0),
            pos(18, 0, 0), pos(22, 4, 0), pos(20, 4, 0), pos(18, 4, 0)
        )
        list.addAll(leftClaw)
        list.addAll(rightClaw)

        // Legs
        for (y in listOf(6, 8, 10)) {
            list.add(pos(4, y, 0))
            list.add(pos(2, y, 0))
            list.add(pos(18, y, 0))
            list.add(pos(20, y, 0))
        }

        // Tail
        for (x in listOf(8, 10, 12, 14)) {
            list.add(pos(x, 12, 0))
            list.add(pos(x, 14, 0))
        }

        for (x in listOf(8, 10, 12, 14)) {
            for (y in listOf(5, 7, 9, 11)) {
                list.add(pos(x, y, 1))
            }
        }
        list.add(pos(2, 2, 1))
        list.add(pos(4, 2, 1))
        list.add(pos(18, 2, 1))
        list.add(pos(20, 2, 1))

        list.add(pos(10, 7, 2))
        list.add(pos(12, 7, 2))
        list.add(pos(10, 9, 2))
        list.add(pos(12, 9, 2))

        return Constellation(
            id = "crab",
            name = "Golden Crab",
            difficulty = Difficulty.MEDIUM,
            description = "A lively maritime constellation with prominent pincers and protective armor.",
            positions = list
        )
    }

    // 94 tiles
    private fun createButterfly(): Constellation {
        val list = mutableListOf<TilePosition>()
        // Body center line
        for (y in 2..14 step 2) {
            list.add(pos(11, y, 0))
        }
        // Left upper wing
        for (x in listOf(3, 5, 7, 9)) {
            for (y in listOf(2, 4, 6, 8)) {
                list.add(pos(x, y, 0))
            }
        }
        // Right upper wing
        for (x in listOf(13, 15, 17, 19)) {
            for (y in listOf(2, 4, 6, 8)) {
                list.add(pos(x, y, 0))
            }
        }
        // Left lower wing
        for (x in listOf(5, 7, 9)) {
            for (y in listOf(10, 12, 14)) {
                list.add(pos(x, y, 0))
            }
        }
        // Right lower wing
        for (x in listOf(13, 15, 17)) {
            for (y in listOf(10, 12, 14)) {
                list.add(pos(x, y, 0))
            }
        }
        // Wing tips
        list.add(pos(1, 4, 0))
        list.add(pos(1, 6, 0))
        list.add(pos(21, 4, 0))
        list.add(pos(21, 6, 0))
        list.add(pos(3, 12, 0))
        list.add(pos(19, 12, 0))
        // Antennae
        list.add(pos(9, 0, 0))
        list.add(pos(13, 0, 0))
        list.add(pos(7, 0, 0))
        list.add(pos(15, 0, 0))
        // Tail
        list.add(pos(11, 16, 0))

        for (y in listOf(4, 6, 8, 10, 12)) {
            list.add(pos(11, y, 1))
        }
        list.add(pos(11, 2, 1))
        // Wing patterns
        for (x in listOf(5, 7)) {
            for (y in listOf(4, 6)) {
                list.add(pos(x, y, 1))
            }
        }
        for (x in listOf(15, 17)) {
            for (y in listOf(4, 6)) {
                list.add(pos(x, y, 1))
            }
        }
        list.add(pos(7, 10, 1))
        list.add(pos(7, 12, 1))
        list.add(pos(5, 12, 1))
        list.add(pos(15, 10, 1))
        list.add(pos(15, 12, 1))
        list.add(pos(17, 12, 1))
        list.add(pos(9, 6, 1))
        list.add(pos(13, 6, 1))

        list.add(pos(6, 5, 2))
        list.add(pos(16, 5, 2))
        list.add(pos(11, 6, 2))
        list.add(pos(11, 8, 2))

        return Constellation(
            id = "butterfly",
            name = "Emerald Butterfly",
            difficulty = Difficulty.MEDIUM,
            description = "Graceful mirrored wings layered with intricate patterns and delicate edges.",
            positions = list
        )
    }

    // 144 tiles: the standard Shanghai Turtle
    private fun createClassicTurtle(): Constellation {
        val list = mutableListOf<TilePosition>()
        for (x in 6..28 step 2) list.add(pos(x, 0, 0))
        for (x in 10..24 step 2) list.add(pos(x, 2, 0))
        for (x in 8..26 step 2) list.add(pos(x, 4, 0))
        for (x in 6..28 step 2) list.add(pos(x, 6, 0))
        for (x in 6..28 step 2) list.add(pos(x, 8, 0))
        for (x in 8..26 step 2) list.add(pos(x, 10, 0))
        for (x in 10..24 step 2) list.add(pos(x, 12, 0))
        for (x in 6..28 step 2) list.add(pos(x, 14, 0))
        // Outer wings
        list.add(pos(2, 7, 0))
        list.add(pos(4, 7, 0))
        list.add(pos(30, 7, 0))
        list.add(pos(32, 7, 0))
        list.remove(pos(6, 0, 0))
        list.remove(pos(28, 0, 0))

        for (gx in 0 until 6) {
            for (gy in 0 until 6) {
                list.add(pos(12 + gx * 2, 3 + gy * 2, 1))
            }
        }

        for (gx in 0 until 4) {
            for (gy in 0 until 4) {
                list.add(pos(14 + gx * 2, 5 + gy * 2, 2))
            }
        }

        for (gx in 0 until 2) {
            for (gy in 0 until 2) {
                list.add(pos(16 + gx * 2, 7 + gy * 2, 3))
            }
        }

        list.add(pos(17, 8, 4))
        list.add(pos(0, 7, 0)) // Far outer wing

        return Constellation(
            id = "classic_turtle",
            name = "Classic Turtle",
            difficulty = Difficulty.MEDIUM,
            description = "The timeless 144-tile Shanghai Turtle formation revered by Mahjong players worldwide.",
            positions = list
        )
    }

    // 108 tiles
    private fun createFortress(): Constellation {
        val list = mutableListOf<TilePosition>()
        // Bastions
        val bastionOrigins = listOf(
            2 to 2,
            18 to 2,
            2 to 14,
            18 to 14
        )
        for ((bx, by) in bastionOrigins) {
            for (dx in 0..2) {
                for (dy in 0..2) {
                    list.add(pos(bx + dx * 2, by + dy * 2, 0))
                }
            }
            for (dx in 0..1) {
                for (dy in 0..1) {
                    list.add(pos(bx + 1 + dx * 2, by + 1 + dy * 2, 1))
                }
            }
            list.add(pos(bx + 2, by + 2, 2))
        }

        // Connecting walls
        for (x in listOf(8, 10, 12, 14, 16)) {
            list.add(pos(x, 4, 0))
            list.add(pos(x, 16, 0))
        }
        for (y in listOf(8, 10, 12)) {
            list.add(pos(4, y, 0))
            list.add(pos(20, y, 0))
        }
        // Extra wall thickness
        list.add(pos(6, 8, 0))
        list.add(pos(18, 8, 0))
        list.add(pos(6, 12, 0))
        list.add(pos(18, 12, 0))

        // Central keep
        for (x in listOf(9, 11, 13, 15)) {
            for (y in listOf(7, 9, 11, 13)) {
                list.add(pos(x, y, 0))
            }
        }
        for (x in listOf(10, 12, 14)) {
            for (y in listOf(8, 10, 12)) {
                list.add(pos(x, y, 1))
            }
        }
        list.add(pos(12, 4, 1))
        list.add(pos(11, 9, 2))
        list.add(pos(13, 9, 2))
        list.add(pos(11, 11, 2))
        list.add(pos(13, 11, 2))
        list.add(pos(12, 9, 3))
        list.add(pos(12, 11, 3))

        return Constellation(
            id = "fortress",
            name = "Iron Fortress",
            difficulty = Difficulty.HARD,
            description = "Heavily fortified ramparts with four corner bastions and a heavily guarded keep.",
            positions = list
        )
    }

    // 128 tiles
    private fun createCastle(): Constellation {
        val list = mutableListOf<TilePosition>()
        // Base
        for (x in 2..20 step 2) {
            for (y in 2..16 step 2) {
                list.add(pos(x, y, 0))
            }
        }
        // Corner towers
        val towers = listOf(
            listOf(pos(2, 2, 1), pos(4, 2, 1), pos(2, 4, 1), pos(4, 4, 1)),
            listOf(pos(18, 2, 1), pos(20, 2, 1), pos(18, 4, 1), pos(20, 4, 1)),
            listOf(pos(2, 14, 1), pos(4, 14, 1), pos(2, 16, 1), pos(4, 16, 1)),
            listOf(pos(18, 14, 1), pos(20, 14, 1), pos(18, 16, 1), pos(20, 16, 1))
        )
        for (t in towers) list.addAll(t)

        // Central great hall
        for (x in listOf(8, 10, 12, 14)) {
            for (y in listOf(6, 8, 10, 12)) {
                list.add(pos(x, y, 1))
            }
        }

        // Inner keep
        for (x in listOf(9, 11, 13)) {
            for (y in listOf(7, 9, 11)) {
                list.add(pos(x, y, 2))
            }
        }
        list.add(pos(3, 3, 2))
        list.add(pos(19, 3, 2))

        // Battlements high crown
        list.add(pos(10, 8, 3))
        list.add(pos(12, 8, 3))
        list.add(pos(10, 10, 3))
        list.add(pos(12, 10, 3))

        // Keep finial
        list.add(pos(11, 9, 4))

        return Constellation(
            id = "castle",
            name = "Dragon Castle",
            difficulty = Difficulty.HARD,
            description = "Grand fortress battlements, high watchtowers, and multi-tier defensive redoubts.",
            positions = list
        )
    }

    // 144 tiles
    private fun createDragon(): Constellation {
        val list = mutableListOf<TilePosition>()
        // Head
        for (x in listOf(24, 26, 28)) {
            for (y in listOf(2, 4, 6)) {
                list.add(pos(x, y, 0))
            }
        }
        for (x in listOf(24, 26, 28)) {
            for (y in listOf(2, 4, 6)) {
                list.add(pos(x, y, 1))
            }
        }
        list.add(pos(25, 3, 2))
        list.add(pos(27, 3, 2))
        list.add(pos(25, 5, 2))
        list.add(pos(27, 5, 2))
        list.add(pos(25, 4, 3))
        list.add(pos(27, 4, 3))
        // Whiskers
        list.add(pos(30, 3, 0))
        list.add(pos(30, 5, 0))

        // Body: (x, top y) columns, each a base pair + ridge + spine
        val upperBody = listOf(
            22 to 6, 20 to 7, 18 to 8, 16 to 8, 14 to 8,
            12 to 7, 10 to 6, 8 to 5, 6 to 5, 4 to 6
        )
        val lowerBody = listOf(
            4 to 12, 6 to 13, 8 to 14, 10 to 14,
            12 to 14, 14 to 14, 16 to 14, 18 to 13
        )
        for ((x, y) in upperBody + lowerBody) {
            list.add(pos(x, y, 0))
            list.add(pos(x, y + 2, 0))
            list.add(pos(x, y + 1, 1))
            list.add(pos(x, y + 1, 2))
        }
        // Dorsal plates
        for ((x, y) in upperBody.filter { it.first in listOf(6, 10, 14, 18) }) {
            list.add(pos(x, y + 1, 3))
        }

        // Coil joining upper and lower body
        for (x in listOf(0, 2)) {
            for (y in listOf(8, 10, 12)) {
                list.add(pos(x, y, 0))
            }
        }
        list.add(pos(1, 9, 1))
        list.add(pos(1, 11, 1))
        list.add(pos(1, 10, 2))

        // Claws
        val clawBases = listOf(
            4 to 0,
            14 to 2,
            6 to 19,
            14 to 19
        )
        for ((cx, cy) in clawBases) {
            list.add(pos(cx, cy, 0))
            list.add(pos(cx + 2, cy, 0))
            list.add(pos(cx, cy + 2, 0))
            list.add(pos(cx + 2, cy + 2, 0))
            list.add(pos(cx + 1, cy + 1, 1))
            list.add(pos(cx + 1, cy + 1, 2))
        }

        // Tail
        list.add(pos(20, 13, 0))
        list.add(pos(20, 15, 0))
        list.add(pos(22, 12, 0))
        list.add(pos(22, 14, 0))
        list.add(pos(24, 12, 0))
        list.add(pos(26, 13, 0))
        list.add(pos(20, 14, 1))
        list.add(pos(22, 13, 1))
        list.add(pos(20, 14, 2))

        return Constellation(
            id = "dragon",
            name = "Celestial Dragon",
            difficulty = Difficulty.HARD,
            description = "A magnificent coiled dragon spanning the heavens with layered spinal ridges.",
            positions = list
        )
    }

    // 144 tiles
    private fun createPagoda(): Constellation {
        val list = mutableListOf<TilePosition>()
        // Base
        for (x in 2..24 step 2) {
            for (y in 6..12 step 2) {
                list.add(pos(x, y, 0))
            }
        }
        // First eaves
        for (x in 4..22 step 2) {
            for (y in 7..11 step 2) {
                list.add(pos(x, y, 1))
            }
        }
        // Second eaves
        for (x in 6..20 step 2) {
            for (y in 7..11 step 2) {
                list.add(pos(x, y, 2))
            }
        }
        // Third eaves
        for (x in 8..18 step 2) {
            for (y in 8..10 step 2) {
                list.add(pos(x, y, 3))
            }
        }
        // Spire roof
        for (x in 10..16 step 2) {
            for (y in 8..10 step 2) {
                list.add(pos(x, y, 4))
            }
        }
        // Spire finial
        list.add(pos(12, 8, 5))
        list.add(pos(14, 8, 5))
        list.add(pos(12, 10, 5))
        list.add(pos(14, 10, 5))

        // Side prayer bells
        for (y in listOf(6, 8, 10)) {
            list.add(pos(0, y, 0))
            list.add(pos(-2, y, 0))
            list.add(pos(-1, y, 1))

            list.add(pos(26, y, 0))
            list.add(pos(28, y, 0))
            list.add(pos(27, y, 1))
        }

        return Constellation(
            id = "pagoda",
            name = "Nine Dragon Pagoda",
            difficulty = Difficulty.EXPERT,
            description = "An intricate five-tiered temple spire offering a supreme architectural challenge.",
            positions = list
        )
    }
}
