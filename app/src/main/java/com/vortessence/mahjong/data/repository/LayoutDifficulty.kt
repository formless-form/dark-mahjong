package com.vortessence.mahjong.data.repository

import com.vortessence.mahjong.data.model.Difficulty

/**
 * Each layout's clear rate without help, averaged over a random-pair player and a highest-pair-first
 * player, 1,000 deals each (LevelDifficultyMeasure). Levels and Layouts run highest first, and the
 * labels come from it. Measure a new layout: MEASURE_LEVELS=1000 ./gradlew testFossDebugUnitTest --tests '*LevelDifficultyMeasure'
 */
internal object LayoutDifficulty {

    val clearRate: Map<String, Float> = mapOf(
        "jade_pillar" to 0.9940f,
        "bamboo_spire" to 0.9875f,
        "mini_turtle" to 0.9825f,
        "dragon_saber" to 0.9820f,
        "celestial_lantern" to 0.9515f,
        "torii_arch" to 0.9440f,
        "paper_lantern" to 0.9405f,
        "temple_bell" to 0.9395f,
        "cross" to 0.9370f, // Four Winds Cross
        "diamond" to 0.9285f, // Imperial Diamond
        "celadon_hourglass" to 0.9275f,
        "pyramid" to 0.9100f, // Terraced Pyramid
        "stone_bridge" to 0.8815f,
        "cascading_falls" to 0.8595f,
        "phoenix_tower" to 0.8410f,
        "heart" to 0.8120f, // Jade Heart
        "imperial_monolith" to 0.8085f,
        "teapot" to 0.7985f,
        "ruyi_scepter" to 0.7900f,
        "rice_bowl" to 0.7870f,
        "pillar_of_heaven" to 0.7785f,
        "twin_peaks" to 0.7780f,
        "maple_leaf" to 0.7730f,
        "bonsai_tree" to 0.7355f,
        "folding_fan" to 0.7320f,
        "stone_arch" to 0.7270f,
        "crab" to 0.7145f, // Golden Crab
        "red_fox" to 0.6810f,
        "butterfly" to 0.6725f, // Emerald Butterfly
        "crane_in_flight" to 0.6475f,
        "paper_kite" to 0.6460f,
        "mah_828570775" to 0.6415f, // Fence
        "pagoda_spire" to 0.6145f, // Grand Pagoda Spire
        "tortoise" to 0.6125f,
        "twin_pyramids" to 0.5885f,
        "fortress" to 0.5745f, // Iron Fortress
        "mah_1088330854" to 0.5655f, // Steps
        "junk_ship" to 0.5590f,
        "mountain_pass" to 0.5565f,
        "three_tier_pagoda" to 0.5535f,
        "great_wall" to 0.5415f,
        "mah_3101065593" to 0.5380f, // White Dragon
        "mah_2232624362" to 0.5195f, // Flower
        "standing_owl" to 0.4765f,
        "city_skyline" to 0.4705f,
        "mah_3316304069" to 0.4580f, // Green Dragon
        "sleeping_cat" to 0.4355f,
        "comet" to 0.4335f,
        "mah_2036373485" to 0.4210f, // Abstract Garden
        "mah_2005925237" to 0.4150f, // Block
        "hot_air_balloon" to 0.4090f,
        "feast_table" to 0.4055f,
        "koi_carp" to 0.3785f,
        "mah_979337589" to 0.3775f, // Tree
        "steam_train" to 0.3765f,
        "dragon_boat" to 0.3730f,
        "moon_bridge" to 0.3675f,
        "mah_554306016" to 0.3640f, // Cactus
        "castle" to 0.3620f, // Dragon Castle
        "mah_2283982827" to 0.3575f, // Heart
        "ziggurat" to 0.3575f,
        "mah_2788427354" to 0.3420f, // Pyramid
        "mah_1048565772" to 0.3295f, // Invader
        "treasure_chest" to 0.3230f,
        "mah_179167734" to 0.3150f, // Elephant
        "mah_130253071" to 0.3070f, // Fortress
        "mah_773823936" to 0.3055f, // Quad
        "blue_whale" to 0.2980f,
        "mah_1593808196" to 0.2890f, // Blossom
        "mah_2756835802" to 0.2885f, // Stairs
        "lotus_pond" to 0.2860f,
        "mah_2926596175" to 0.2840f, // Platypus
        "mah_450647981" to 0.2830f, // Potted Plant
        "mah_3906642412" to 0.2825f, // Diamond
        "classic_turtle" to 0.2765f,
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
        // Mah's on-side boards (1.4.16)
        "mah_4277830847" to 0.7767f, // Lattice Gate
        "mah_1830783848" to 0.7384f, // Dragonfly
        "mah_1492921254" to 0.6667f, // Ceremony Hall
        "mah_544607066" to 0.6433f, // Broken Wall
        "mah_709938609" to 0.6283f, // Courtyard
        "mah_852527327" to 0.6134f, // Plum Blossom
        "mah_2485677966" to 0.6067f, // Peacock
        "mah_2390420165" to 0.5933f, // Year of the Snake
        "mah_588942537" to 0.5900f, // Lucky Cat
        "mah_679913014" to 0.5467f, // Ice Crystals
        "mah_1861655803" to 0.5466f, // Labyrinth
        "mah_2257355141" to 0.5400f, // Temple Gate
        "mah_1566704849" to 0.5300f, // Year of the Boar
        "mah_1344148453" to 0.5167f, // Stone Gateway
        "mah_1752166124" to 0.5016f, // Silk Moth
        "mah_399933105" to 0.4934f, // Stag Beetle
        "mah_1901484058" to 0.4867f, // Sun Temple
        "mah_2866125446" to 0.4733f, // Snowflake
        "mah_3878481316" to 0.4600f, // Year of the Monkey
        "mah_2680421030" to 0.4567f, // Year of the Goat
        "mah_1378681582" to 0.4550f, // Drum Tower
        "mah_2674681711" to 0.4516f, // Compass Rose
        "mah_3354968048" to 0.4484f, // Window Lattice
        "mah_1504777776" to 0.4450f, // Year of the Rat
        "mah_3262148258" to 0.4367f, // Chrysanthemum
        "mah_2951481859" to 0.4284f, // Bonfire
        "mah_2038574563" to 0.4133f, // Year of the Rabbit
        "mah_2650995104" to 0.4100f, // Three Mountains
        "mah_1001794235" to 0.4083f, // Twin Seals
        "mah_2219513689" to 0.3933f, // Spider
        "mah_1488318675" to 0.3866f, // Year of the Tiger
        "mah_2842823842" to 0.3817f, // Watchtower
        "mah_1953985656" to 0.3783f, // Goldfish
        "mah_3955482880" to 0.3783f, // Year of the Dragon
        "mah_113014417" to 0.3716f, // Scorpion
        "mah_2683869356" to 0.3667f, // Ripple Pool
        "mah_2810552848" to 0.3500f, // Year of the Rooster
        "mah_2175883231" to 0.3483f, // Sea Turtle
        "mah_2579333550" to 0.3334f, // Pavilion
        "mah_2072264091" to 0.2917f, // Ladybird
        "mah_4047697972" to 0.2600f, // Year of the Ox
        "mah_2971925539" to 0.2183f, // Bull's Head
        "mah_2410278133" to 0.2117f, // Orchid
        "mah_1463206324" to 0.1900f, // Hourglass Gate
        "mah_3707661927" to 0.1817f, // Willow Leaf
        "mah_1647425443" to 0.1784f, // Go Board
        "mah_2206664028" to 0.1600f, // Picture Frame
        // Made for the Journey (2026-11-14)
        "paper_boat" to 0.9450f,
        "ancient_coin" to 0.8365f,
        "paper_umbrella" to 0.7885f,
        "winter_plum" to 0.7570f,
        "giant_panda" to 0.7515f,
        "snow_peak" to 0.6510f,
        "tea_pavilion" to 0.5515f,
        "sampan" to 0.5440f,
        "longevity_peach" to 0.5115f,
        "autumn_bloom" to 0.4140f,
        "festival_drum" to 0.3815f,
        "frontier_wall" to 0.1865f,
    )

    /** Easy from 90%, Medium from 60%, Hard from 35%, Expert below. */
    fun label(rate: Float): Difficulty = when {
        rate >= 0.90f -> Difficulty.EASY
        rate >= 0.60f -> Difficulty.MEDIUM
        rate >= 0.35f -> Difficulty.HARD
        else -> Difficulty.EXPERT
    }

    /**
     * [highest] in the old numbering -> the furthest any layout they could play moved to (maybe opening
     * levels between). Past the last level (all cleared) stays so.
     */
    fun highestAfterReorder(highest: Int, newLevelOf: (String) -> Int?, before: List<String> = levelsBeforeMeasuredOrder): Int {
        // All cleared: one past the furthest any moved to
        if (highest > before.size) return before.mapNotNull(newLevelOf).maxOrNull()?.plus(1)?.coerceAtLeast(highest) ?: highest
        val reached = before.take(highest).mapNotNull(newLevelOf).maxOrNull() ?: return highest
        return maxOf(highest, reached)
    }

    /** 1.4.16's numbering (88 levels), before the Journey's twelve, to carry unlocks over. */
    val levelsBeforeJourney100: List<String> = listOf(
        "jade_pillar",
        "bamboo_spire",
        "dragon_saber",
        "celestial_lantern",
        "torii_arch",
        "celadon_hourglass",
        "cascading_falls",
        "phoenix_tower",
        "imperial_monolith",
        "ruyi_scepter",
        "pillar_of_heaven",
        "mah_4277830847",
        "mah_1830783848",
        "mah_1492921254",
        "mah_544607066",
        "mah_828570775",
        "mah_709938609",
        "pagoda_spire",
        "mah_852527327",
        "mah_2485677966",
        "mah_2390420165",
        "mah_588942537",
        "mah_1088330854",
        "mah_679913014",
        "mah_1861655803",
        "mah_2257355141",
        "mah_3101065593",
        "mah_1566704849",
        "mah_2232624362",
        "mah_1344148453",
        "mah_1752166124",
        "mah_399933105",
        "mah_1901484058",
        "mah_2866125446",
        "mah_3878481316",
        "mah_3316304069",
        "mah_2680421030",
        "mah_1378681582",
        "mah_2674681711",
        "mah_3354968048",
        "mah_1504777776",
        "mah_3262148258",
        "mah_2951481859",
        "mah_2036373485",
        "mah_2005925237",
        "mah_2038574563",
        "mah_2650995104",
        "mah_1001794235",
        "mah_2219513689",
        "mah_1488318675",
        "mah_2842823842",
        "mah_1953985656",
        "mah_3955482880",
        "mah_979337589",
        "mah_113014417",
        "mah_2683869356",
        "mah_554306016",
        "mah_2283982827",
        "mah_2810552848",
        "mah_2175883231",
        "mah_2788427354",
        "mah_2579333550",
        "mah_1048565772",
        "mah_179167734",
        "mah_130253071",
        "mah_773823936",
        "mah_2072264091",
        "mah_1593808196",
        "mah_2756835802",
        "mah_2926596175",
        "mah_450647981",
        "mah_3906642412",
        "mah_2881142435",
        "mah_738084013",
        "mah_1870648008",
        "mah_2770595719",
        "mah_4047697972",
        "mah_2630082713",
        "mah_2215397839",
        "mah_1843357205",
        "mah_2971925539",
        "mah_2410278133",
        "mah_2838350141",
        "mah_702162641",
        "mah_1463206324",
        "mah_3707661927",
        "mah_1647425443",
        "mah_2206664028",
    )

    /** 1.4.15's numbering, before 1.4.16's 47 on-side Mah boards, to carry unlocks over. */
    val levelsBeforeWideMah: List<String> = listOf(
        "jade_pillar",
        "bamboo_spire",
        "dragon_saber",
        "celestial_lantern",
        "torii_arch",
        "celadon_hourglass",
        "cascading_falls",
        "phoenix_tower",
        "imperial_monolith",
        "ruyi_scepter",
        "pillar_of_heaven",
        "mah_828570775",
        "pagoda_spire",
        "mah_1088330854",
        "mah_3101065593",
        "mah_2232624362",
        "mah_3316304069",
        "mah_2036373485",
        "mah_2005925237",
        "mah_979337589",
        "mah_554306016",
        "mah_2283982827",
        "mah_2788427354",
        "mah_1048565772",
        "mah_179167734",
        "mah_130253071",
        "mah_773823936",
        "mah_1593808196",
        "mah_2756835802",
        "mah_2926596175",
        "mah_450647981",
        "mah_3906642412",
        "mah_2881142435",
        "mah_738084013",
        "mah_1870648008",
        "mah_2770595719",
        "mah_2630082713",
        "mah_2215397839",
        "mah_1843357205",
        "mah_2838350141",
        "mah_702162641",
    )

    /** Numbering before the clear-rate order (1.3.6 and earlier), to carry unlocks over. */
    val levelsBeforeMeasuredOrder: List<String> = listOf(
        "jade_pillar",
        "bamboo_spire",
        "dragon_saber",
        "celestial_lantern",
        "torii_arch",
        "celadon_hourglass",
        "cascading_falls",
        "phoenix_tower",
        "mah_2283982827", // Heart
        "mah_828570775", // Fence
        "mah_1870648008", // Mushroom
        "mah_1088330854", // Steps
        "mah_1843357205", // Clover
        "mah_2232624362", // Flower
        "mah_979337589", // Tree
        "imperial_monolith",
        "ruyi_scepter",
        "pillar_of_heaven",
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
        "mah_450647981", // Potted Plant
        "mah_554306016", // Cactus
        "mah_2005925237", // Block
        "mah_702162641", // Red Dragon
        "mah_1593808196", // Blossom
        "mah_773823936", // Quad
    )
}
