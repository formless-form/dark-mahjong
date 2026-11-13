package com.vortessence.mahjong.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.WideBoards
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.BoardSolvabilityGenerator
import com.vortessence.mahjong.engine.MahjongGameEngine
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
import kotlin.random.Random

/**
 * Tile set candidates, each on a few tables: a close look (the tutorial's few big tiles) and a
 * full board, by firelight on walnut and by the lamp on the green mat.
 * Rendered into build/tile-kits/<kit>-<scene>.png.
 * Run: ./gradlew testFossDebugUnitTest --tests '*TileKitPreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class TileKitPreview {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun back() {
        TileKits.preview = null
    }

    private data class Scene(val layout: String, val surface: TableSurface, val fire: Boolean, val name: String)

    private val scenes = listOf(
        Scene(ConstellationRepository.TUTORIAL_ID, TableSurface.WALNUT, true, "1-close-walnut-fire"),
        Scene("pagoda", TableSurface.WALNUT, true, "2-board-walnut-fire"),
        Scene("pagoda", TableSurface.GREEN_MAT, false, "3-board-felt-lamp"),
    )

    private fun stateOf(scene: Scene): GameState {
        val constellation = ConstellationRepository.getById(scene.layout)!!
        val tiles = MahjongGameEngine.recalculateFreedom(BoardSolvabilityGenerator.generateSolvableBoard(constellation, Random(7)))
        return MahjongGameEngine.startNewGame(constellation).copy(tiles = tiles)
    }

    // One screen per test (a rule sets its content once), its scene changed between shots
    private fun scenes(kit: TileKit) {
        PerfSwitches.off = emptySet()
        TileWear.prepareNow()
        TileRenderer.TileAtlas.bakeInline = true
        TileKits.preview = kit
        var scene by mutableStateOf(scenes.first())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val state = remember(scene) { stateOf(scene) }
            MahjongTheme {
                GameScreen(
                    gameState = state, levelIndex = null,
                    settings = GameSettings(tableSurface = scene.surface, fireplaceLit = scene.fire, wideBoards = WideBoards.PORTRAIT),
                    winMessage = null,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        for (next in scenes) {
            scene = next
            compose.mainClock.advanceTimeBy(1500)
            compose.onRoot().captureRoboImage("build/tile-kits/${kit.name.lowercase()}-${next.name}.png")
        }
    }

    @Test fun ivory() = scenes(TileKits.IVORY)
    // The set as chosen (2026-11-13), and the step before it, a shade lighter
    @Test fun charcoal() = scenes(TileKits.CHARCOAL.copy(name = "s-charcoal"))
    @Test fun lighter() = scenes(TERRAZZO.copy(name = "p-lighter", edge = 1.1f))

    private companion object {
        // Light charcoal, a touch warm to sit with the cream
        val CHARCOAL = TileKit(
            name = "charcoal",
            faceTop = Color(0xFF605F5D),
            faceBottom = Color(0xFF52514F),
            bevelHighlight = Color(0xFF9A9894),
            bevelAlpha = 0.5f,
            side = Color(0xFF41403E),
            backing = TileKits.IVORY.backing,
            ageing = TileKits.IVORY.ageing,
            art = Color(0xFFF3E6C4),
            raised = false,
            cutShadow = Color(0x99000000),
            cutLight = Color(0x40FFFFFF),
            wear = 0.35f,
        )
        // A shade darker charcoal, the pictures raised in bright white with a dark edge all round
        val WHITE = CHARCOAL.copy(
            faceTop = Color(0xFF545351),
            faceBottom = Color(0xFF474644),
            side = Color(0xFF383735),
            art = Color.White,
            raised = true,
            cutShadow = Color(0xB0000000),
            cutLight = Color(0x40FFFFFF),
            edge = 0.75f,
        )
        val TERRAZZO = WHITE.copy(backing = Color(0xFF262524), terrazzo = 1f)
        val RAISED_SHADOW = Color(0xA0000000)
        val RAISED_LIGHT = Color(0x55FFFFFF)
        val BRIGHT_CREAM = Color(0xFFFFF4D8)
    }
}
