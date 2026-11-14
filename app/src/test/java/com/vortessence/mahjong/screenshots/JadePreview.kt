package com.vortessence.mahjong.screenshots

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
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
 * A more lifelike Jade (Gareth, 2026-11-14, from a photo of a polished jade stone): the face mottled
 * with soft clouds of darker and lighter green, pale wispy veins and fine flecks, still glazed, with
 * Jade's white raised pictures and colour bar. Numbered 7 to 10 as sent (another set's round had
 * 1 to 6); Gareth chose 9, now TileKits.JADE. Into build/jade/<n>-<name>-<what>.png.
 * Run: ./gradlew testFossDebugUnitTest --tests '*JadePreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class JadePreview {
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
        File(OUT).mkdirs()
        FileOutputStream("$OUT/$name-faces.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // Eight faces large: the six and nine of bamboo (told apart by the colour cue), the nine of
    // circles, a character, a wind, a dragon, a season and the one of circles
    private fun bigSheet(kit: TileKit, name: String, tileW: Float = 260f) {
        val faces = listOf(
            Tile(TileSuit.BAMBOO, 6), Tile(TileSuit.BAMBOO, 9), Tile(TileSuit.CIRCLE, 9), Tile(TileSuit.CHARACTER, 5),
            Tile(TileSuit.WIND, 1), Tile(TileSuit.DRAGON, 1), Tile(TileSuit.SEASON, 1), Tile(TileSuit.CIRCLE, 1),
        )
        val across = 4
        val tileH = tileW * 1.33f
        val gap = tileW * 0.15f
        val w = (across * (tileW + gap) + gap).toInt()
        val h = ((faces.size / across) * (tileH + gap) + gap).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val lamp = LampColour.tiles().let { Color(it[0], it[1], it[2]) }
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color(0xFF3A2416))
            faces.forEachIndexed { i, tile ->
                TileRenderer.drawSample(this, tile, gap + (i % across) * (tileW + gap), gap + (i / across) * (tileH + gap), tileW, tileH, kit, look = i)
            }
            drawRect(lamp, blendMode = BlendMode.Modulate)
        }
        FileOutputStream("$OUT/$name-big.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private data class Scene(val surface: TableSurface, val fire: Boolean, val name: String)

    private fun variant(kit: TileKit, name: String) {
        PerfSwitches.off = emptySet()
        TileWear.prepareNow()
        TileRenderer.TileAtlas.bakeInline = true
        sheet(kit, name)
        bigSheet(kit, name)
        TileKits.preview = kit
        // Quad
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
            Snapshot.withMutableSnapshot { scene = next }
            compose.mainClock.advanceTimeBy(1500)
            compose.onRoot().captureRoboImage("$OUT/$name-${next.name}.png")
        }
    }

    // Jade's mottling: dark clouds, light clouds, and the pale veins and flecks
    private fun mottled(top: Long, bottom: Long, side: Long, backing: Long, dark: Long, light: Long, vein: Long, strength: Float = 1f) =
        TileKits.JADE.copy(
            faceTop = Color(top), faceBottom = Color(bottom), side = Color(side), backing = Color(backing),
            mottle = strength, mottleTones = listOf(Color(dark), Color(light), Color(vein)),
        )

    // Jade as it shipped in 1.4.15: plain dark jade, unmottled
    @Test fun n7() = variant(
        TileKits.JADE.copy(
            faceTop = Color(0xFF2D5D47), faceBottom = Color(0xFF1F4635), side = Color(0xFF19382A), backing = Color(0xFF11281A),
            mottle = 0f,
        ),
        "7-jade-as-it-shipped",
    )
    @Test fun n8() = variant(
        mottled(0xFF2D5D47, 0xFF1F4635, 0xFF19382A, 0xFF11281A, dark = 0xFF0E2E20, light = 0xFF4E8E68, vein = 0xFFBFE3CC),
        "8-dark-jade-mottled",
    )
    // Gareth's pick (2026-11-14), now TileKits.JADE itself
    @Test fun n9() = variant(TileKits.JADE, "9-mid-jade-mottled")
    @Test fun n10() = variant(
        mottled(0xFF3B9550, 0xFF2C7A40, 0xFF215C30, 0xFF143A1F, dark = 0xFF14552A, light = 0xFF79C98A, vein = 0xFFDDF2E0),
        "10-bright-jade-mottled",
    )

    private companion object {
        const val OUT = "build/jade"
    }
}
