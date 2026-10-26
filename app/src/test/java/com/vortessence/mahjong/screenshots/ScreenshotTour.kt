package com.vortessence.mahjong.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.vortessence.mahjong.data.model.ContinueInfo
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.engine.TutorialScript
import com.vortessence.mahjong.ui.components.PredictiveBackState
import com.vortessence.mahjong.ui.components.StalemateDialog
import com.vortessence.mahjong.ui.components.TableSurfaceRenderer
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.components.fireworksSnapshot
import com.vortessence.mahjong.ui.components.predictiveBack
import com.vortessence.mahjong.ui.screens.ConstellationSelectScreen
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.screens.LevelSelectScreen
import com.vortessence.mahjong.ui.screens.MainMenuScreen
import com.vortessence.mahjong.ui.screens.SettingsScreen
import com.vortessence.mahjong.ui.screens.TitleMenuStyle
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

    @Composable
    private fun TitleScreen(style: TitleMenuStyle = TitleMenuStyle.SANS) = MainMenuScreen(
        continueInfo = ContinueInfo("Level 3: Celestial Lantern", "40 tiles remaining"),
        menuStyle = style,
        onPlayLevels = {},
        onChooseConstellation = {},
        onSettings = {},
    )

    @Test
    fun title() {
        compose.setContent { MahjongTheme { TitleScreen() } }
        shoot("title")
    }

    @Test
    fun titleSerifMenu() {
        compose.setContent { MahjongTheme { TitleScreen(TitleMenuStyle.SERIF) } }
        shoot("title_serif_menu")
    }

    private val someProgress = Progress(highestLevelReached = 4, completedConstellations = setOf("tutorial"))

    @Test
    fun levels() {
        compose.setContent {
            MahjongTheme {
                LevelSelectScreen(
                    progress = someProgress.copy(
                        highestLevelReached = 4,
                        completedConstellations = ConstellationRepository.getLevelConstellations(false).take(3).map { it.id }.toSet() + "tutorial",
                    ),
                    levels = ConstellationRepository.getLevelConstellations(false),
                    onLevelSelected = {},
                    onBack = {},
                )
            }
        }
        shoot("levels")
    }

    @Test
    fun layouts() {
        compose.setContent {
            MahjongTheme { ConstellationSelectScreen(progress = someProgress, onConstellationSelected = {}, onBack = {}) }
        }
        shoot("layouts")
    }

    // Boards ported from Mah: a tall one, and one of the widest kept (tiles about 30 dp)
    private fun mahGame(name: String, surface: TableSurface, shot: String) {
        TileWear.prepareNow()
        val state = MahjongGameEngine.startNewGame(ConstellationRepository.mahConstellations.first { it.name == name })
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state,
                    levelIndex = null,
                    settings = GameSettings(tableSurface = surface),
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onChooseConstellation = {}, onMainMenu = {},
                )
            }
        }
        shoot(shot)
    }

    @Test fun mahCactus() = mahGame("Cactus", TableSurface.GREEN_MAT, "mah_cactus")
    @Test fun mahFence() = mahGame("Fence", TableSurface.WALNUT, "mah_fence")

    @Test
    fun layoutsMah() {
        compose.setContent {
            MahjongTheme { ConstellationSelectScreen(progress = someProgress, onConstellationSelected = {}, onBack = {}) }
        }
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Clover"))
        shoot("layouts_mah")
    }

    @Test
    fun tutorial() {
        TileWear.prepareNow()
        val tutorial = ConstellationRepository.tutorialConstellation
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = MahjongGameEngine.startNewGame(tutorial),
                    levelIndex = ConstellationRepository.TUTORIAL_LEVEL,
                    tutorialStep = TutorialScript.steps.first(),
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onChooseConstellation = {}, onMainMenu = {},
                )
            }
        }
        shoot("tutorial")
    }

    @Test
    fun noMovesDialog() {
        TileWear.prepareNow()
        compose.setContent {
            MahjongTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF0C1110)))
                StalemateDialog(canUndo = true, hasFreePairs = false, onReshuffle = {}, onUndo = {}, onRestart = {})
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/no_moves.png")
    }

    // Settings part-way through a back swipe from the left edge, the title screen behind it
    @Test
    fun predictiveBackMidGesture() {
        val back = PredictiveBackState().apply { progress = 0.7f }
        compose.setContent {
            MahjongTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TitleScreen()
                    Box(Modifier.fillMaxSize().predictiveBack(back).background(MaterialTheme.colorScheme.background)) {
                        SettingsScreen(settings = GameSettings(), onSettingsChanged = {}, onBack = {})
                    }
                }
            }
        }
        shoot("predictive_back")
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

    // Pinched in to about 2.2x near the top of the board: the zoom-out button appears
    @Test
    fun gameZoomedIn() {
        game(TableSurface.WALNUT, 11)
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            val c = Offset(centerX, height * 0.35f)
            pinch(
                start0 = c - Offset(60f, 0f), end0 = c - Offset(132f, 0f),
                start1 = c + Offset(60f, 0f), end1 = c + Offset(132f, 0f),
                durationMillis = 400,
            )
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Zoom out to the whole board").assertExists()
        shoot("game_zoomed")
    }

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
        compose.onNodeWithText("Thai straw").performScrollTo()
        shoot("settings")
    }
}
