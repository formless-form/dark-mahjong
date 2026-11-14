package com.vortessence.mahjong.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.WideBoards
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.BoardSolvabilityGenerator
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.ui.components.ImperialImages
import com.vortessence.mahjong.ui.components.PerfSwitches
import com.vortessence.mahjong.ui.components.TileKits
import com.vortessence.mahjong.ui.components.TileRenderer
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Imperial on a real board, drawn by the game itself from the Imperial images (ImperialImages):
 * Quad on the green mat by the lamp, and on walnut by the fire. Into
 * build/imperial-tile/board-felt-lamp.png and board-walnut-fire.png. The images come with the
 * Play build only, so this does nothing where they are absent (the open-source build's tests).
 * Run: ./gradlew testPlayDebugUnitTest --tests '*ImperialBoardPreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class ImperialBoardPreview {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun back() {
        TileKits.preview = null
    }

    private data class Scene(val surface: TableSurface, val fire: Boolean, val name: String)

    @Test
    fun board() {
        PerfSwitches.off = emptySet()
        TileWear.prepareNow()
        ImperialImages.assets = RuntimeEnvironment.getApplication().assets
        assumeTrue("The Imperial images are not in this build", ImperialImages.load() != null)
        TileRenderer.TileAtlas.bakeInline = true
        TileKits.preview = TileKits.IMPERIAL
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
            // Applied at once: a change waiting on the next frame was never seen (ColourCuePreview)
            Snapshot.withMutableSnapshot { scene = next }
            compose.mainClock.advanceTimeBy(1500)
            compose.onRoot().captureRoboImage("build/imperial-tile/${next.name}.png")
        }
    }
}
