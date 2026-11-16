package com.vortessence.mahjong.ui.components

import androidx.compose.ui.graphics.Color
import com.vortessence.mahjong.data.model.TileSet
import com.vortessence.mahjong.ui.theme.TileBevelHighlight
import com.vortessence.mahjong.ui.theme.TileIvoryBottom
import com.vortessence.mahjong.ui.theme.TileIvoryTop
import com.vortessence.mahjong.ui.theme.TileSideIvoryBottom
import com.vortessence.mahjong.ui.theme.TileSideWoodBacking

/**
 * A tile set's colours and how its pictures are cut. [IVORY] is the game's own; the others theme
 * the same drawings. [art] null keeps each picture's colours, else all are that colour. Pictures
 * are [raised] (lit above, shadowed below) or engraved (shadowed upper walls, light lip below).
 */
data class TileKit(
    val name: String,
    val faceTop: Color,
    val faceBottom: Color,
    val bevelHighlight: Color,
    val bevelAlpha: Float,
    val side: Color,
    val backing: Color,
    // A faint age tint over every tile
    val ageing: Color,
    val art: Color?,
    val raised: Boolean,
    val cutShadow: Color,
    val cutLight: Color,
    // Scratches, grime, dings: 1 on ivory; far fainter on dark faces, where pale hairlines stand out
    val wear: Float = 1f,
    // Raised pictures only: a dark outline at cutShadow x this, so pale pictures stand clear (0: none)
    val edge: Float = 0f,
    // Stone chips in the face, with mica flecks that glint in the lamp and fire (0: none)
    val terrazzo: Float = 0f,
    // How far the tiles' lamp and firelight lose their warmth (0 to 1), so pale pictures stay white
    // by the fire; the table keeps its warm light
    val neutralLight: Float = 0f,
    // With [art] null: picture colours (blue, green, red) swapped, e.g. brighter for a dark face
    // (missing ones kept)
    val recolour: Map<Color, Color> = emptyMap(),
    // Raised pictures' edge colour (gold, say) instead of [cutShadow]'s
    val edgeColour: Color? = null,
    // Reach beyond the picture, in cut depths (0.7 its own)
    val edgeReach: Float = 0.7f,
    // A disc per picture colour, so faces told apart by colour stay apart in one-colour sets
    val colourDots: Boolean = false,
    // Size as a fraction of tile width; ring null: a fine dark one
    val dotPlace: DotPlace = DotPlace.LOWER_LEFT,
    val dotSize: Float = 0.072f,
    val dotRing: Color? = null,
    // Bar length as a fraction of tile width; glow in its own colours (0: none)
    val barLength: Float = 0.46f,
    val glow: Float = 0f,
    // LED-like bar: a near-white core per colour, its halo lighting the face
    val led: Boolean = false,
    // Glow spread (1: as designed; small: a hairline), and how near white an LED core burns
    val glowSpread: Float = 1f,
    val ledCore: Float = 0.65f,
    // Bar colours lightened towards white, and a fine light rim
    val barLighten: Float = 0f,
    val barRim: Boolean = false,
    // Outline darkness (0 to 1) and width (bar heights); colours split by a dark line or a gap of
    // [barGap] heights
    val barOutline: Float = 0.5f,
    val barOutlineWidth: Float = 0.18f,
    val barSplit: BarSplit = BarSplit.NONE,
    val barGap: Float = 0.2f,
    // Cut into the face: shadowed upper inside edge, light lip below, colours a little deeper
    val barEngraved: Boolean = false,
    // Raised bar's sharp emboss: cast shadow, top highlight, underside shade (0: soft sheen only)
    val barEmboss: Float = 0f,
    // A wider, softer carved bevel, lit on top, shaded at the foot (0: the fine one alone)
    val bevel: Float = 0f,
    // Per-tile warmth and lightness variation, as in a real set (0: all alike)
    val toneVariation: Float = 0f,
    // Shading of lower layers against the top one (1: the game's own)
    val layerCue: Float = 1f,
    // Per-tile flowing grain and pores, with occasional knots (0: none)
    val woodGrain: Float = 0f,
    // Dark heartwood streaks, as olive wood's (0: none)
    val woodFigure: Float = 0f,
    // Jade-like mottle: dark and light clouds, pale veins and flecks, per tile (0: none), in
    // [mottleTones] (dark clouds, light clouds, veins). Jade, 2026-11-14
    val mottle: Float = 0f,
    val mottleTones: List<Color> = emptyList(),
    // Picture scale within the face, and lift (fraction of tile height) to leave the dots room
    val artScale: Float = 0.85f,
    val artLift: Float = 0f,
    // Drawn from shipped images (ImperialImages): a body per look, each engraving over it; no wear,
    // glaze or glare. The colours only show before the images load
    val stone: Boolean = false,
)

/** How a colour bar's colours are kept apart. */
enum class BarSplit { NONE, LINE, GAP }

/** Where a tile's colour-code dots go. */
enum class DotPlace {
    LOWER_LEFT,
    // Centred
    BOTTOM,
    // From the bottom
    LEFT_COLUMN,
    UPPER_LEFT,
    // Not dots: one short bar, split between the colours
    BOTTOM_BAR,
}

/** As the tile drawings use them. */
object FaceColours {
    val BLUE = Color(0xFF1E39CD)
    val GREEN = Color(0xFF006F00)
    val RED = Color(0xFFB93C3C)
    val ALL = listOf(RED, GREEN, BLUE)
}

object TileKits {
    val IVORY = TileKit(
        name = "Ivory",
        faceTop = TileIvoryTop,
        faceBottom = TileIvoryBottom,
        bevelHighlight = TileBevelHighlight,
        bevelAlpha = 0.6f,
        side = TileSideIvoryBottom,
        backing = TileSideWoodBacking,
        ageing = Color(0xFFB08A4A),
        art = null,
        raised = false,
        cutShadow = Color(0x5A3A2A18),
        cutLight = Color(0x99FFFDF6),
    )

    /**
     * Charcoal terrazzo, raised dark-edged white pictures (soft gold in the warm light) and a colour
     * bar. Gareth's pick, 2026-11-13 (TileKitPreview), over cream or wood backings, carved
     * pictures, cream, cooler white, gold and neutral light.
     */
    val CHARCOAL = TileKit(
        name = "Terrazzo",
        faceTop = Color(0xFF3F3E3C),
        faceBottom = Color(0xFF343331),
        bevelHighlight = Color(0xFF9A9894),
        bevelAlpha = 0.5f,
        side = Color(0xFF292826),
        backing = Color(0xFF1B1A19),
        ageing = Color(0xFFB08A4A),
        art = Color.White,
        raised = true,
        cutShadow = Color(0xB0000000),
        cutLight = Color(0x40FFFFFF),
        wear = 0.35f,
        edge = 1.1f,
        terrazzo = 1f,
        // One-colour pictures lose colour cues (bamboo six vs nine), so a bar carries them, split by
        // a fine dark line (Gareth's pick, 2026-11-13, over dots, glows and coloured pictures)
        colourDots = true,
        dotPlace = DotPlace.BOTTOM_BAR,
        dotSize = 0.09f,
        artScale = 0.8f,
        artLift = 0.04f,
        barOutline = 0.4f,
        barOutlineWidth = 0.12f,
        barSplit = BarSplit.LINE,
        barGap = 0.22f,
    )

    /**
     * Settings offers a choice of sets (since 1.4.15). A switch in case a set must be withdrawn
     * again, as 1.4.14 withdrew the first Charcoal (colour-only faces looked alike; fixed by the bar).
     */
    const val OFFERED = true

    fun of(set: TileSet): TileKit = when (set) {
        TileSet.IVORY -> IVORY
        TileSet.CHARCOAL -> CHARCOAL
        TileSet.JADE -> JADE
        TileSet.WOOD -> WOOD
        // Builds without the images (open-source) fall back to Ivory
        TileSet.IMPERIAL -> if (ImperialImages.available) IMPERIAL else IVORY
    }

    /**
     * Polished mid-green jade, mottled like the real stone, with Charcoal's pictures and bar, a
     * soft bevel and tile-to-tile tone. Modelled on Gareth's photo of polished jade; his pick,
     * 2026-11-14 (JadePreview mock-up 9, over darker and lighter greens).
     */
    val JADE = CHARCOAL.copy(
        name = "Jade",
        faceTop = Color(0xFF2F7A4C),
        faceBottom = Color(0xFF235F3A),
        side = Color(0xFF1C4A2E),
        backing = Color(0xFF12301E),
        bevelHighlight = Color(0xFF9CC9B0),
        art = Color(0xFFF4ECD6),
        terrazzo = 0f,
        wear = 0.4f,
        bevel = 0.8f,
        toneVariation = 1.2f,
        mottle = 1f,
        // Deep clouds, pale clouds, veins and flecks
        mottleTones = listOf(Color(0xFF103D22), Color(0xFF5DAE72), Color(0xFFCDEBD3)),
    )

    /**
     * Figured olive wood, mock-up 4 three shades darker (Gareth, 2026-11-13), with Charcoal's
     * pictures and bar.
     */
    val WOOD = CHARCOAL.copy(
        name = "Olivewood",
        faceTop = Color(0xFF977949),
        faceBottom = Color(0xFF826439),
        side = Color(0xFF5D4524),
        backing = Color(0xFF2A1D10),
        bevelHighlight = Color(0xFFC8A880),
        terrazzo = 0f,
        woodGrain = 1.6f,
        // Gareth's pick of three strengths (2026-11-13)
        woodFigure = 0.6f,
        toneVariation = 0.8f,
        wear = 0.5f,
    )

    /**
     * The reward set: matte, roughly carved grey granite, engraving painted gold, carved and
     * painted colour bar (Gareth's design and picks from numbered previews, 2026-11-14). Drawn from
     * ImperialImages (Play build only, else not offered); these colours show only until they load.
     */
    val IMPERIAL = CHARCOAL.copy(
        name = "Imperial",
        faceTop = Color(0xFF646568),
        faceBottom = Color(0xFF595A5D),
        side = Color(0xFF444548),
        backing = Color(0xFF28292B),
        bevelHighlight = Color(0xFF8A8884),
        bevelAlpha = 0.3f,
        art = Color(0xFFE2BA71),
        raised = false,
        cutShadow = Color(0xD0000000),
        cutLight = Color(0x5AFFFFFF),
        terrazzo = 0f,
        wear = 0f,
        toneVariation = 0f,
        stone = true,
    )

    /** Settings' choice, kept up to date by MahjongBoard. */
    @Volatile
    var chosen: TileKit = IVORY

    /** Previews only: overrides [chosen]. */
    @Volatile
    var preview: TileKit? = null

    /** A change redraws the tile atlas. */
    val current: TileKit get() = preview ?: chosen
}
