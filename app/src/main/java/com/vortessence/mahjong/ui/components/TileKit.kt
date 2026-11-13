package com.vortessence.mahjong.ui.components

import androidx.compose.ui.graphics.Color
import com.vortessence.mahjong.BuildConfig
import com.vortessence.mahjong.data.model.TileSet
import com.vortessence.mahjong.ui.theme.TileBevelHighlight
import com.vortessence.mahjong.ui.theme.TileIvoryBottom
import com.vortessence.mahjong.ui.theme.TileIvoryTop
import com.vortessence.mahjong.ui.theme.TileSideIvoryBottom
import com.vortessence.mahjong.ui.theme.TileSideWoodBacking

/**
 * A set of tiles: the colours of their faces, sides and backing, and how the faces' pictures and
 * text are cut. [IVORY] is the game's own set; the others are themes over the same drawings.
 *
 * [art] null keeps each picture's own colours; otherwise every picture is in that one colour.
 * The pictures are [raised] (embossed: lit along their upper edges, shadowed below) or carved into
 * the face (engraved: shadowed along their upper walls, a light lip below).
 */
data class TileKit(
    val name: String,
    val faceTop: Color,
    val faceBottom: Color,
    val bevelHighlight: Color,
    val bevelAlpha: Float,
    val side: Color,
    val backing: Color,
    // The face's age, a faint tint over every tile
    val ageing: Color,
    val art: Color?,
    val raised: Boolean,
    val cutShadow: Color,
    val cutLight: Color,
    // How strongly years of play show (scratches, grime, dings): 1 on ivory; far fainter on a dark
    // face, where every pale hairline stands out
    val wear: Float = 1f,
    // Raised pictures only: a dark edge all round each shape, as strong as cutShadow times this,
    // so a pale picture stands clear of the face (0: none)
    val edge: Float = 0f,
    // Terrazzo: stone chips of many sizes and tones set in the face, as strong as this (0: none),
    // with a few flecks of mica that glint as the lamp and the fire catch them
    val terrazzo: Float = 0f,
    // How far the lamp and firelight on these tiles lose their warmth (0 to 1), so pale pictures
    // stay white by the fire. The table keeps its own warm light
    val neutralLight: Float = 0f,
    // With [art] null: each of the pictures' own colours (blue, green, red) shown as another, a
    // brighter or paler one for a dark face (missing ones kept)
    val recolour: Map<Color, Color> = emptyMap(),
    // The edge round raised pictures in a colour of its own (gold, say), rather than [cutShadow]'s
    val edgeColour: Color? = null,
    // How far the edge reaches beyond the picture, in the cut's depths (0.7 its own)
    val edgeReach: Float = 0.7f,
    // Colour-code dots: one small disc for each of the picture's colours, at the face's lower left,
    // so faces told apart by colour stay apart when the pictures are one colour
    val colourDots: Boolean = false,
    // Where and how the dots go, how big (a fraction of the tile's width across), and the ring
    // round each (null: a fine dark one)
    val dotPlace: DotPlace = DotPlace.LOWER_LEFT,
    val dotSize: Float = 0.072f,
    val dotRing: Color? = null,
    // The bar's length (a fraction of the tile's width), and how strongly it glows in its own
    // colours (0: none)
    val barLength: Float = 0.46f,
    val glow: Float = 0f,
    // The bar lit like a coloured LED: a near-white core down each colour, its halo adding light
    // to the face around it
    val led: Boolean = false,
    // How far the glow spreads (1: as designed; small, a hairline at the bar's edge), and how
    // near white an LED's core burns
    val glowSpread: Float = 1f,
    val ledCore: Float = 0.65f,
    // The bar's colours lightened towards white by this much, and a fine light rim round it
    val barLighten: Float = 0f,
    val barRim: Boolean = false,
    // The bar's outline: how dark (0 to 1) and how wide (in the bar's heights); and the colours kept
    // apart by a dark line ([BarSplit.LINE]) or a gap ([BarSplit.GAP]) as wide as [barGap] heights
    val barOutline: Float = 0.5f,
    val barOutlineWidth: Float = 0.18f,
    val barSplit: BarSplit = BarSplit.NONE,
    val barGap: Float = 0.2f,
    // The bar cut into the face rather than set on it: shadowed along its upper inside edge, a light
    // lip below, its colours a little deeper
    val barEngraved: Boolean = false,
    // How strongly a raised bar is embossed (0: the soft sheen alone): a crisp shadow cast below it,
    // a hard highlight along its top edge and shade along its underside, all sharp-edged
    val barEmboss: Float = 0f,
    // A wider, softer bevel round the face (0: the fine one alone), lit along its top edge and
    // shaded along its foot, as a carved tile's
    val bevel: Float = 0f,
    // How much each tile's tone differs from the next (0: all alike): a little warmer or cooler,
    // lighter or darker, as the pieces of a real set
    val toneVariation: Float = 0f,
    // How strongly lower layers are shaded against the top one (1: the game's own)
    val layerCue: Float = 1f,
    // Wood grain in the face, as strong as this (0: none): each tile's own, flowing lines and pores,
    // now and then a knot with the grain sweeping round it
    val woodGrain: Float = 0f,
    // Figured wood: dark heartwood streaks wandering through the grain, as olive wood's, as
    // strong as this (0: none)
    val woodFigure: Float = 0f,
    // The picture's size within the face (0.85 its own), and how far it moves up (a fraction of the
    // tile's height), to leave the dots room
    val artScale: Float = 0.85f,
    val artLift: Float = 0f,
)

/** How a colour bar's colours are kept apart. */
enum class BarSplit { NONE, LINE, GAP }

/** Where a tile's colour-code dots go. */
enum class DotPlace {
    // A row at the lower left
    LOWER_LEFT,
    // A row centred along the bottom
    BOTTOM,
    // A column up the left edge, from the bottom
    LEFT_COLUMN,
    // A row at the upper left
    UPPER_LEFT,
    // Not dots: one short bar along the bottom, split between the colours
    BOTTOM_BAR,
}

/** The pictures' own colours, as the tile drawings use them. */
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
     * Charcoal terrazzo, its pictures raised in white with a dark edge all round (the warm light
     * makes the white a soft gold), each face's colours in a bar along its bottom. Chosen by Gareth on 2026-11-13 from renders of the
     * alternatives (TileKitPreview): a cream or wood backing, carved pictures, cream, cooler
     * white, gold, and light drained of its warmth.
     */
    val CHARCOAL = TileKit(
        name = "Charcoal",
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
        // One-colour pictures lose what told some faces apart (the six and nine of bamboo), so a
        // colour bar along the bottom carries each face's colours, split evenly, a fine dark line
        // between them (Gareth's pick, 2026-11-13, over dots, glows and coloured pictures)
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
     * Whether Settings offers a choice of sets. Off in Play builds since 1.4.14 (Gareth found two
     * faces told apart only by colour looked alike in Charcoal, 2026-11-13): the row is hidden and
     * every board is drawn in Ivory, a choice made before kept for when it returns. Test builds
     * (perfHooks) offer it, to try Charcoal with its colour bar on a phone.
     */
    val OFFERED: Boolean get() = BuildConfig.PERF_HOOKS || previewOffered

    /** Previews only: Settings offers the sets, as a test build's does. */
    @Volatile
    var previewOffered = false

    fun of(set: TileSet): TileKit = when (set) {
        TileSet.IVORY -> IVORY
        TileSet.CHARCOAL -> CHARCOAL
        TileSet.JADE -> JADE
        TileSet.WOOD -> WOOD
    }

    /**
     * Dark jade, with Charcoal's white raised pictures and colour bar: smooth stone with a soft
     * bevel and a little tone from tile to tile. Chosen by Gareth on 2026-11-13 (the darkest of
     * three shades).
     */
    val JADE = CHARCOAL.copy(
        name = "Jade",
        faceTop = Color(0xFF2D5D47),
        faceBottom = Color(0xFF1F4635),
        side = Color(0xFF19382A),
        backing = Color(0xFF11281A),
        bevelHighlight = Color(0xFF9CC9B0),
        art = Color(0xFFF4ECD6),
        terrazzo = 0f,
        wear = 0.4f,
        bevel = 0.8f,
        toneVariation = 1.2f,
    )

    /**
     * Figured olive wood, a little darker than olive is when new (Gareth asked for mock-up 4's
     * olive three shades darker, 2026-11-13): wild grain of its own on every tile, dark heartwood
     * streaks, now and then a knot, with Charcoal's white raised pictures and colour bar.
     */
    val WOOD = CHARCOAL.copy(
        name = "Wood",
        faceTop = Color(0xFF977949),
        faceBottom = Color(0xFF826439),
        side = Color(0xFF5D4524),
        backing = Color(0xFF2A1D10),
        bevelHighlight = Color(0xFFC8A880),
        terrazzo = 0f,
        woodGrain = 1.6f,
        // Light figure: Gareth's pick of three strengths (2026-11-13)
        woodFigure = 0.6f,
        toneVariation = 0.8f,
        wear = 0.5f,
    )

    /** The set chosen in Settings (MahjongBoard keeps it up to date). */
    @Volatile
    var chosen: TileKit = IVORY

    /** Previews only: a set to try, over the chosen one. */
    @Volatile
    var preview: TileKit? = null

    /** The set the tiles are drawn in. A change makes the tile atlas draw its images again. */
    val current: TileKit get() = preview ?: chosen
}
