package com.vortessence.mahjong.screenshots

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TileSuit
import com.vortessence.mahjong.data.model.WideBoards
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.BoardSolvabilityGenerator
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.ui.components.BarSplit
import com.vortessence.mahjong.ui.components.DotPlace
import com.vortessence.mahjong.ui.components.FaceColours
import com.vortessence.mahjong.ui.components.LampColour
import com.vortessence.mahjong.ui.components.PerfSwitches
import com.vortessence.mahjong.ui.components.TileKit
import com.vortessence.mahjong.ui.components.TileKits
import com.vortessence.mahjong.ui.components.TileRenderer
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Ways to bring colour back to Charcoal, so faces told apart by colour stay apart and a full board
 * reads: each as every face on one sheet, and as Quad on the green mat by the lamp and on walnut
 * by the fire. Into build/colour-cues/<variant>-<what>.png.
 * Run: ./gradlew testFossDebugUnitTest --tests '*ColourCuePreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class ColourCuePreview {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun back() {
        TileKits.preview = null
    }

    private fun sheet(kit: TileKit, name: String, tileW: Float = 90f) {
        val tileH = tileW * 1.33f
        val gap = tileW * 0.25f
        val w = (9 * (tileW + gap) + gap).toInt()
        val h = (TileSuit.entries.size * (tileH + gap) + gap).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val lamp = LampColour.tiles().let { Color(it[0], it[1], it[2]) }
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color(0xFF3A2416))
            TileSuit.entries.forEachIndexed { r, suit ->
                for (v in 1..9) {
                    val tile = Tile(suit, v)
                    if (TileRenderer.getTileVector(tile) == null) continue
                    TileRenderer.drawSample(this, tile, gap + (v - 1) * (tileW + gap), gap + r * (tileH + gap), tileW, tileH, kit, look = v + r)
                }
            }
            drawRect(lamp, blendMode = BlendMode.Modulate)
        }
        File("build/colour-cues").mkdirs()
        FileOutputStream("build/colour-cues/$name-faces.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // A few faces large: the six, seven and nine of bamboo and the nine of circles
    private fun bigSheet(kit: TileKit, name: String, tileW: Float = 260f) {
        val faces = listOf(Tile(TileSuit.BAMBOO, 6), Tile(TileSuit.BAMBOO, 7), Tile(TileSuit.BAMBOO, 9), Tile(TileSuit.CIRCLE, 9))
        val tileH = tileW * 1.33f
        val gap = tileW * 0.15f
        val w = (faces.size * (tileW + gap) + gap).toInt()
        val h = (tileH + gap * 2).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val lamp = LampColour.tiles().let { Color(it[0], it[1], it[2]) }
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color(0xFF3A2416))
            faces.forEachIndexed { i, tile -> TileRenderer.drawSample(this, tile, gap + i * (tileW + gap), gap, tileW, tileH, kit, look = i) }
            drawRect(lamp, blendMode = BlendMode.Modulate)
        }
        FileOutputStream("build/colour-cues/$name-big.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private data class Scene(val surface: TableSurface, val fire: Boolean, val name: String)

    private fun variant(kit: TileKit, name: String) {
        PerfSwitches.off = emptySet()
        TileWear.prepareNow()
        TileRenderer.TileAtlas.bakeInline = true
        sheet(kit, name)
        bigSheet(kit, name)
        TileKits.preview = kit
        val layout = ConstellationRepository.getById("mah_773823936")!!
        val tiles = MahjongGameEngine.recalculateFreedom(BoardSolvabilityGenerator.generateSolvableBoard(layout, Random(7)))
        val state = MahjongGameEngine.startNewGame(layout).copy(tiles = tiles)
        val scenes = listOf(Scene(TableSurface.GREEN_MAT, false, "board-felt-lamp"), Scene(TableSurface.WALNUT, true, "board-walnut-fire"))
        var scene by mutableStateOf(scenes.first())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val s = remember(scene) { scene }
            MahjongTheme {
                GameScreen(
                    gameState = state, levelIndex = null,
                    settings = GameSettings(tableSurface = s.surface, fireplaceLit = s.fire, wideBoards = WideBoards.PORTRAIT),
                    winMessage = null,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        for (next in scenes) {
            scene = next
            compose.mainClock.advanceTimeBy(1500)
            compose.onRoot().captureRoboImage("build/colour-cues/$name-${next.name}.png")
        }
    }

    private val base = TileKits.CHARCOAL
    private val bright = mapOf(FaceColours.RED to Color(0xFFF0645A), FaceColours.GREEN to Color(0xFF3CC85A), FaceColours.BLUE to Color(0xFF6E8CFF))
    private val pastel = mapOf(FaceColours.RED to Color(0xFFF7B1AC), FaceColours.GREEN to Color(0xFF9DE3AC), FaceColours.BLUE to Color(0xFFB6C5FF))
    private val gold = Color(0xFFE0B860)
    // Every picture a little smaller (the same for all), so the dots have room
    private val dots = TileKits.CHARCOAL.copy(colourDots = true, artScale = 0.8f)
    // The colour bar along the bottom (Gareth's pick, 2026-11-13)
    // A crisper outline for the embossed bars
    private val sharp = TileKits.CHARCOAL.copy(barOutline = 0.6f, barOutlineWidth = 0.08f)
    private val bar = dots.copy(dotPlace = DotPlace.BOTTOM_BAR, dotSize = 0.09f, artLift = 0.04f)
    private val cream = Color(0xFFF2E6C8)

    // Ideas to make the tiles look better (2026-11-13), numbered as sent
    private val ivoryBar = TileKits.IVORY.copy(
        colourDots = true, dotPlace = DotPlace.BOTTOM_BAR, dotSize = 0.09f, artScale = 0.8f, artLift = 0.04f,
        barOutline = 0.4f, barOutlineWidth = 0.12f, barSplit = BarSplit.LINE, barGap = 0.22f,
    )
    // Okabe and Ito's colours, told apart with any colour vision
    private val colourSafe = mapOf(FaceColours.RED to Color(0xFFD55E00), FaceColours.GREEN to Color(0xFF009E73), FaceColours.BLUE to Color(0xFF0072B2))
    private val jade = TileKits.CHARCOAL.copy(
        name = "Jade",
        faceTop = Color(0xFF3E7A5E), faceBottom = Color(0xFF2C5E47), side = Color(0xFF234D3A), backing = Color(0xFF1A3A2C),
        bevelHighlight = Color(0xFF9CC9B0), art = Color(0xFFF4ECD6), terrazzo = 0f, wear = 0.4f, bevel = 0.8f, toneVariation = 1.2f,
    )
    private val bone = TileKits.IVORY.copy(
        name = "Bone",
        faceTop = Color(0xFFF3EAD3), faceBottom = Color(0xFFE2D4B4), side = Color(0xFFCDBB95), backing = Color(0xFF6B4A2B),
        ageing = Color(0xFF9A6A30), wear = 1.2f, bevel = 0.6f, toneVariation = 1.5f,
    )

    // Darker jade, and Charcoal a shade darker (2026-11-13)
    private val jadeDarker = jade.copy(faceTop = Color(0xFF356B52), faceBottom = Color(0xFF25523E), side = Color(0xFF1E4232), backing = Color(0xFF15301F))
    private val jadeDarkest = jade.copy(faceTop = Color(0xFF2D5D47), faceBottom = Color(0xFF1F4635), side = Color(0xFF19382A), backing = Color(0xFF11281A))
    private val charcoalBefore = TileKits.CHARCOAL.copy(
        faceTop = Color(0xFF494846), faceBottom = Color(0xFF3D3C3A), side = Color(0xFF2F2E2C), backing = Color(0xFF201F1E),
    )

    // Wood sets (2026-11-13): Charcoal's white pictures and colour bar, the face in grained wood
    private fun wood(top: Long, bottom: Long, side: Long, backing: Long, grain: Float = 1f) = TileKits.CHARCOAL.copy(
        name = "Wood", faceTop = Color(top), faceBottom = Color(bottom), side = Color(side), backing = Color(backing),
        bevelHighlight = Color(0xFFC8A880), terrazzo = 0f, woodGrain = grain, toneVariation = 0.8f, wear = 0.5f,
    )

    @Test fun w1() = variant(wood(0xFF6B4A32, 0xFF553A27, 0xFF432D1E, 0xFF2A1C12), "1-walnut")
    @Test fun w2() = variant(wood(0xFF9C6B3C, 0xFF84592F, 0xFF6A4625, 0xFF3A2614), "2-teak")
    @Test fun w3() = variant(wood(0xFF6E3328, 0xFF5A2820, 0xFF47201A, 0xFF2A120E), "3-rosewood")
    @Test fun w4() = variant(wood(0xFFB89A6A, 0xFFA3855A, 0xFF7E6440, 0xFF3E2E1C, grain = 1.6f), "4-olive-wood")
    @Test fun w5() = variant(wood(0xFF3A2A20, 0xFF2C2018, 0xFF22180F, 0xFF140E08, grain = 1.3f), "5-ebony")
    @Test fun w6() = variant(wood(0xFF6B4A32, 0xFF553A27, 0xFF432D1E, 0xFF2A1C12, grain = 1.7f), "6-walnut-strong-grain")
}
