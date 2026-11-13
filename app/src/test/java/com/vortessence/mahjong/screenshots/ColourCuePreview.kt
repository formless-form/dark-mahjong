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

    private data class Scene(val surface: TableSurface, val fire: Boolean, val name: String)

    private fun variant(kit: TileKit, name: String) {
        PerfSwitches.off = emptySet()
        TileWear.prepareNow()
        TileRenderer.TileAtlas.bakeInline = true
        sheet(kit, name)
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
    private val cream = Color(0xFFF2E6C8)

    @Test fun ivory() = variant(TileKits.IVORY, "0-ivory")
    @Test fun dotsAsBefore() = variant(dots, "1-dots-lower-left-small")
    @Test fun dotsBigger() = variant(dots.copy(dotSize = 0.1f, artLift = 0.03f), "2-dots-lower-left-bigger")
    @Test fun dotsBottom() = variant(dots.copy(dotPlace = DotPlace.BOTTOM, dotSize = 0.09f, artLift = 0.04f), "3-dots-bottom-centre")
    @Test fun dotsColumn() = variant(dots.copy(dotPlace = DotPlace.LEFT_COLUMN, dotSize = 0.09f), "4-dots-left-column")
    @Test fun dotsUpper() = variant(dots.copy(dotPlace = DotPlace.UPPER_LEFT, dotSize = 0.09f, artLift = -0.03f), "5-dots-upper-left")
    @Test fun bar() = variant(dots.copy(dotPlace = DotPlace.BOTTOM_BAR, dotSize = 0.09f, artLift = 0.04f), "6-colour-bar-bottom")
    @Test fun dotsBottomGold() = variant(dots.copy(dotPlace = DotPlace.BOTTOM, dotSize = 0.09f, artLift = 0.04f, dotRing = gold), "7-dots-bottom-gold-ring")
    @Test fun dotsLowerLeftGold() = variant(dots.copy(dotSize = 0.1f, artLift = 0.03f, dotRing = gold), "8-dots-lower-left-gold-ring")
}
