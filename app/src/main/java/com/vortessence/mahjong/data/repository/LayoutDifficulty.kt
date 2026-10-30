package com.vortessence.mahjong.data.repository

import com.vortessence.mahjong.data.model.Difficulty

/**
 * How hard each layout really is: the share of its deals cleared without hints, undo or
 * reshuffles, averaged over a player taking any free pair at random and one taking the highest
 * pair first, 1,000 deals each (LevelDifficultyMeasure, 2026-10-30). Levels and the Layouts list
 * run from the highest to the lowest, and the difficulty labels come from it. A new layout needs
 * measuring: MEASURE_LEVELS=1000 ./gradlew testFossDebugUnitTest --tests '*LevelDifficultyMeasure'
 */
internal object LayoutDifficulty {

    val clearRate: Map<String, Float> = mapOf(
        "jade_pillar" to 0.9940f, // Jade Pillar
        "bamboo_spire" to 0.9875f, // Bamboo Spire
        "mini_turtle" to 0.9825f, // Mini Turtle
        "dragon_saber" to 0.9820f, // Dragon Saber
        "celestial_lantern" to 0.9515f, // Celestial Lantern
        "torii_arch" to 0.9440f, // Torii Arch
        "cross" to 0.9370f, // Four Winds Cross
        "diamond" to 0.9285f, // Imperial Diamond
        "celadon_hourglass" to 0.9275f, // Celadon Hourglass
        "pyramid" to 0.9100f, // Terraced Pyramid
        "cascading_falls" to 0.8595f, // Cascading Falls
        "phoenix_tower" to 0.8410f, // Phoenix Tower
        "heart" to 0.8120f, // Jade Heart
        "imperial_monolith" to 0.8085f, // Imperial Monolith
        "ruyi_scepter" to 0.7900f, // Ruyi Scepter
        "pillar_of_heaven" to 0.7785f, // Pillar of Heaven
        "crab" to 0.7145f, // Golden Crab
        "butterfly" to 0.6725f, // Emerald Butterfly
        "mah_828570775" to 0.6415f, // Fence
        "pagoda_spire" to 0.6145f, // Grand Pagoda Spire
        "fortress" to 0.5745f, // Iron Fortress
        "mah_1088330854" to 0.5655f, // Steps
        "mah_3101065593" to 0.5380f, // White Dragon
        "mah_2232624362" to 0.5195f, // Flower
        "mah_3316304069" to 0.4580f, // Green Dragon
        "mah_2036373485" to 0.4210f, // Abstract Garden
        "mah_2005925237" to 0.4150f, // Block
        "mah_979337589" to 0.3775f, // Tree
        "mah_554306016" to 0.3640f, // Cactus
        "castle" to 0.3620f, // Dragon Castle
        "mah_2283982827" to 0.3575f, // Heart
        "mah_2788427354" to 0.3420f, // Pyramid
        "mah_1048565772" to 0.3295f, // Invader
        "mah_179167734" to 0.3150f, // Elephant
        "mah_130253071" to 0.3070f, // Fortress
        "mah_773823936" to 0.3055f, // Quad
        "mah_1593808196" to 0.2890f, // Blossom
        "mah_2756835802" to 0.2885f, // Stairs
        "mah_2926596175" to 0.2840f, // Platypus
        "mah_450647981" to 0.2830f, // Potted
        "mah_3906642412" to 0.2825f, // Diamond
        "classic_turtle" to 0.2765f, // Classic Turtle
        "mah_2881142435" to 0.2755f, // Circle
        "mah_738084013" to 0.2735f, // Pawn
        "mah_1870648008" to 0.2705f, // Mushroom
        "mah_2770595719" to 0.2635f, // Black Hole
        "mah_2630082713" to 0.2520f, // Platform
        "mah_2215397839" to 0.2510f, // Fir
        "mah_1843357205" to 0.2465f, // Clover
        "dragon" to 0.2225f, // Celestial Dragon
        "mah_2838350141" to 0.1930f, // Apple
        "mah_702162641" to 0.1930f, // Red Dragon
        "pagoda" to 0.1395f, // Nine Dragon Pagoda
    )

    /** The label for a clear rate: Easy from 90%, Medium from 60%, Hard from 35%, Expert below. */
    fun label(rate: Float): Difficulty = when {
        rate >= 0.90f -> Difficulty.EASY
        rate >= 0.60f -> Difficulty.MEDIUM
        rate >= 0.35f -> Difficulty.HARD
        else -> Difficulty.EXPERT
    }

    /**
     * The levels as they were numbered before they were ordered by clear rate (1.3.6 and earlier),
     * so a player's unlocked levels can be carried over once.
     */
    /**
     * The highest unlocked level after the reorder, for a player whose highest was [highest] in the
     * old numbering: every layout they could play stays playable, so it becomes the furthest any of
     * those layouts moved to (which may also open some levels between). Past the last level means
     * all were cleared, and stays so.
     */
    fun highestAfterReorder(highest: Int, newLevelOf: (String) -> Int?): Int {
        if (highest > levelsBeforeMeasuredOrder.size) return highest
        val reached = levelsBeforeMeasuredOrder.take(highest).mapNotNull(newLevelOf).maxOrNull() ?: return highest
        return maxOf(highest, reached)
    }

    val levelsBeforeMeasuredOrder: List<String> = listOf(
        "jade_pillar", // Jade Pillar
        "bamboo_spire", // Bamboo Spire
        "dragon_saber", // Dragon Saber
        "celestial_lantern", // Celestial Lantern
        "torii_arch", // Torii Arch
        "celadon_hourglass", // Celadon Hourglass
        "cascading_falls", // Cascading Falls
        "phoenix_tower", // Phoenix Tower
        "mah_2283982827", // Heart
        "mah_828570775", // Fence
        "mah_1870648008", // Mushroom
        "mah_1088330854", // Steps
        "mah_1843357205", // Clover
        "mah_2232624362", // Flower
        "mah_979337589", // Tree
        "imperial_monolith", // Imperial Monolith
        "ruyi_scepter", // Ruyi Scepter
        "pillar_of_heaven", // Pillar of Heaven
        "mah_2926596175", // Platypus
        "mah_179167734", // Elephant
        "mah_3101065593", // White Dragon
        "mah_2036373485", // Abstract Garden
        "mah_2881142435", // Circle
        "mah_2215397839", // Fir
        "mah_2788427354", // Pyramid
        "mah_2838350141", // Apple
        "mah_738084013", // Pawn
        "mah_130253071", // Fortress
        "mah_2756835802", // Stairs
        "mah_1048565772", // Invader
        "pagoda_spire", // Grand Pagoda Spire
        "mah_2770595719", // Black Hole
        "mah_3316304069", // Green Dragon
        "mah_2630082713", // Platform
        "mah_3906642412", // Diamond
        "mah_450647981", // Potted
        "mah_554306016", // Cactus
        "mah_2005925237", // Block
        "mah_702162641", // Red Dragon
        "mah_1593808196", // Blossom
        "mah_773823936", // Quad
    )
}
