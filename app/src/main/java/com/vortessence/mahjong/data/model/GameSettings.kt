package com.vortessence.mahjong.data.model

// Order matters: the table shader selects by ordinal. shortName fits the Settings buttons
enum class TableSurface(val displayName: String, val shortName: String) {
    GREEN_MAT("Green mat", "Felt"),
    // Honey-brown elm (Gareth, 2026-11-15), replacing walnut
    ELM_WOOD("Elm wood", "Elm Wood"),
    THAI_STRAW("Thai straw", "Thai Straw Mat"),
    SLATE("Slate", "Slate"),
    // Reward for solving every daily puzzle in a month
    BROCADE("Silk brocade", "Brocade");

    companion object {
        /** Stored name to table, or null; "WALNUT" (replaced 2026-11-15) loads as the elm. */
        fun fromStoredName(name: String): TableSurface? =
            if (name == "WALNUT") ELM_WOOD else entries.firstOrNull { it.name == name }
    }
}

// See TileKits. CHARCOAL and WOOD were renamed Terrazzo and Olivewood (2026-11-13); stored names
// kept so saved choices carry over
enum class TileSet(val shortName: String) {
    IVORY("Ivory"),
    CHARCOAL("Terrazzo"),
    JADE("Jade"),
    WOOD("Olivewood"),
    // Earned by finishing the Journey; only in builds with the Imperial images
    IMPERIAL("Imperial"),
}

// Wide boards (much bigger with the phone on its side): asked once, then the answer is kept
enum class WideBoards(val shortName: String) {
    ASK("Ask"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait"),
}

data class GameSettings(
    val soundEnabled: Boolean = true,
    val soundVolume: Float = 0.65f,
    val musicEnabled: Boolean = true,
    val musicVolume: Float = 0.6f,
    val autoZoomEnabled: Boolean = true,
    val highlightFreeTiles: Boolean = false,
    // Follows the phone's Remove animations; never saved
    val animationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val tableSurface: TableSurface = TableSurface.GREEN_MAT,
    val tileSet: TileSet = TileSet.IVORY,
    // Lamp is always lit; 0..1, 0.5 = natural strength
    val lampBrightness: Float = 0.5f,
    // fireIntensity on the lamp's scale
    val fireplaceLit: Boolean = true,
    val fireIntensity: Float = 0.6f,
    val fireVolume: Float = 0.4f,
    val wideBoards: WideBoards = WideBoards.ASK,
    // Slower phones: fire, fireworks and other motion off; lamp and tile look stay
    val lowPerformance: Boolean = false,
    // Phone's Remove animations, never saved. Low-performance mode keeps the match smash (lighter
    // sparkle) unless this is off
    val phoneAnimates: Boolean = true,
) {
    /** Pair lifts and smashes together, with or without the full sparkle. */
    val matchSmash: Boolean get() = animationsEnabled || (lowPerformance && phoneAnimates)
}

/** First game's frames timed once; a phone that can't keep up is offered Low-performance mode. */
enum class PerfCheck {
    PENDING,
    PASSED,
    // Near the line: fire shadows traced lighter automatically (FireShadows)
    PASSED_CLOSE,
    // To ask about Low-performance mode
    SLOW,
    // Player accepted; mode on
    SWITCHED,
    DECLINED,
    // Player set the mode before the check: never run
    MANUAL,
}

// [stutterPercent]: share of the check's frames that stuttered
data class PerfCheckState(val check: PerfCheck = PerfCheck.PENDING, val stutterPercent: Int = 0)
