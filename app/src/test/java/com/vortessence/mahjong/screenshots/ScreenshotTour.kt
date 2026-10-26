package com.vortessence.mahjong.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.ContinueInfo
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.ui.components.TableSurfaceRenderer
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.components.fireworksSnapshot
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.screens.MainMenuScreen
import com.vortessence.mahjong.ui.screens.SettingsScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the app's screens on the JVM through Robolectric's real Android graphics stack (AGSL
 * shaders and render effects included), at the Pixel 9 Pro's portrait size and density, into
 * build/screenshots. Run: ./gradlew testDebugUnitTest --tests '*ScreenshotTour*'
 *
 * API 36 rather than 37: Espresso, used by the Compose test rule, still calls an InputManager
 * method that API 37 removed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class ScreenshotTour {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/screenshots/$name.png")

    private fun tabletop(surface: TableSurface) {
        compose.setContent {
            MahjongTheme {
                val context = LocalContext.current
                Canvas(Modifier.fillMaxSize()) {
                    TableSurfaceRenderer.drawTableSurface(this, surface, size.width, size.height, context)
                }
            }
        }
        shoot("tabletop_${surface.name.lowercase()}")
    }

    @Test fun tabletopGreenMat() = tabletop(TableSurface.GREEN_MAT)
    @Test fun tabletopWalnut() = tabletop(TableSurface.WALNUT)
    @Test fun tabletopThaiStraw() = tabletop(TableSurface.THAI_STRAW)

    @Test
    fun title() {
        compose.setContent {
            MahjongTheme {
                MainMenuScreen(
                    continueInfo = ContinueInfo("Level 3: Celestial Lantern", "40 tiles remaining"),
                    onPlayLevels = {},
                    onChooseConstellation = {},
                    onSettings = {},
                )
            }
        }
        shoot("title")
    }

    private fun level(index: Int): GameState =
        MahjongGameEngine.startNewGame(ConstellationRepository.getLevel(index, isTablet = false))

    private fun game(surface: TableSurface, levelIndex: Int, state: GameState = level(levelIndex), message: String? = null) {
        TileWear.prepareNow()
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state,
                    levelIndex = levelIndex,
                    settings = GameSettings(tableSurface = surface),
                    winMessage = message,
                    totalLevels = ConstellationRepository.getTotalLevels(false),
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onChooseConstellation = {}, onMainMenu = {},
                )
            }
        }
    }

    @Test fun gameGreenMat() { game(TableSurface.GREEN_MAT, 8); shoot("game_green_mat") }
    @Test fun gameWalnut() { game(TableSurface.WALNUT, 11); shoot("game_walnut") }
    @Test fun gameThaiStraw() { game(TableSurface.THAI_STRAW, 12); shoot("game_thai_straw") }

    @Test
    fun celebration() {
        compose.mainClock.autoAdvance = false
        val won = level(5).let { s -> s.copy(tiles = s.tiles.map { it.copy(isRemoved = true) }, isWon = true) }
        game(TableSurface.WALNUT, 5, won, message = "The mist lifts, and so do the tiles.")
        compose.mainClock.advanceTimeBy(1600)
        shoot("celebration")
    }

    @Test
    fun fireworksAlone() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0C0B))) {
                val draw = fireworksSnapshot(seed = 7L, seconds = 1.9f)
                Canvas(Modifier.fillMaxSize()) { draw(this) }
            }
        }
        shoot("fireworks")
    }

    @Test
    fun settings() {
        compose.setContent {
            MahjongTheme {
                SettingsScreen(settings = GameSettings(tableSurface = TableSurface.THAI_STRAW), onSettingsChanged = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Thai Straw").performScrollTo()
        shoot("settings")
    }
}
