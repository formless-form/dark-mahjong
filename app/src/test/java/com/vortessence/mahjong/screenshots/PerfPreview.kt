package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.BoardSolvabilityGenerator
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.ui.components.PerfSwitches
import com.vortessence.mahjong.ui.components.TileRenderer
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Fixed deals of big layouts at a fixed moment of the fire, at the Pixel 9 Pro's size, for
 * comparing the look before and after rendering changes. Rendered into build/perf-preview/<set>,
 * where <set> is the system property perfSet (default "current").
 * Run: ./gradlew testDebugUnitTest --tests '*PerfPreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class PerfPreview {
    @get:Rule
    val compose = createComposeRule()

    private val set = System.getProperty("perfSet") ?: "current"

    private fun shoot(layout: String, surface: TableSurface, fireplace: Boolean, name: String, off: Set<String> = emptySet()) {
        PerfSwitches.off = off
        TileWear.prepareNow()
        TileRenderer.TileAtlas.bakeInline = true
        val constellation = ConstellationRepository.constellations.first { it.name == layout }
        val tiles = MahjongGameEngine.recalculateFreedom(
            BoardSolvabilityGenerator.generateSolvableBoard(constellation, Random(7)),
        )
        val state = MahjongGameEngine.startNewGame(constellation).copy(tiles = tiles)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state,
                    levelIndex = null,
                    settings = GameSettings(tableSurface = surface, fireplaceLit = fireplace),
                    winMessage = null,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onChooseConstellation = {}, onMainMenu = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/perf-preview/$set/$name.png")
    }

    @Test fun quadWalnutFire() = shoot("Quad", TableSurface.WALNUT, true, "quad-walnut-fire")
    @Test fun quadStrawFire() = shoot("Quad", TableSurface.THAI_STRAW, true, "quad-straw-fire")
    @Test fun quadFeltLamp() = shoot("Quad", TableSurface.GREEN_MAT, false, "quad-felt-lamp")
    @Test fun platformWalnutFire() = shoot("Platform", TableSurface.WALNUT, true, "platform-walnut-fire")

    // The same, drawn shape by shape rather than from the tile atlas, to compare the two
    @Test fun quadWalnutFireLive() = shoot("Quad", TableSurface.WALNUT, true, "quad-walnut-fire-live", setOf("atlas"))
    @Test fun quadFeltLampLive() = shoot("Quad", TableSurface.GREEN_MAT, false, "quad-felt-lamp-live", setOf("atlas"))
}
