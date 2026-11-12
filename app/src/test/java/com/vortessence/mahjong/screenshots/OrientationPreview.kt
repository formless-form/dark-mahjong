package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.WideBoards
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
 * One wide board (Nine Dragon Pagoda) played upright and on its side, lamp alone and with the
 * fireplace, on walnut and the green mat: for weighing how brightly each is lit. Each is drawn
 * again with no tiles (name ending "-bare"), so the tiles' pixels can be told from the table's.
 * Rendered into build/orientation-preview/<set> (-PperfSet, default "current"), with any
 * -PperfOff stages off.
 * Run: ./gradlew testFossDebugUnitTest --tests '*OrientationPreview*' [-PperfSet=name]
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class OrientationPreview {
    @get:Rule
    val compose = createComposeRule()

    private val set = System.getProperty("perfSet") ?: "current"
    private val extraOff = System.getProperty("perfOff")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun shoot(surface: TableSurface, fireplace: Boolean, landscape: Boolean, bare: Boolean) {
        PerfSwitches.off = extraOff.toSet() + if (bare) setOf("tiles") else emptySet()
        TileWear.prepareNow()
        TileRenderer.TileAtlas.bakeInline = true
        // The same deal every time, so renders compare alike
        val constellation = ConstellationRepository.getById("pagoda")!!
        val tiles = MahjongGameEngine.recalculateFreedom(BoardSolvabilityGenerator.generateSolvableBoard(constellation, Random(7)))
        val state = MahjongGameEngine.startNewGame(constellation).copy(tiles = tiles)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state,
                    levelIndex = null,
                    settings = GameSettings(
                        tableSurface = surface,
                        fireplaceLit = fireplace,
                        wideBoards = if (landscape) WideBoards.LANDSCAPE else WideBoards.PORTRAIT,
                    ),
                    winMessage = null,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        val name = listOf(
            surface.name.lowercase(),
            if (landscape) "landscape" else "portrait",
            if (fireplace) "fire" else "lamp",
        ).joinToString("-") + if (bare) "-bare" else ""
        compose.onRoot().captureRoboImage("build/orientation-preview/$set/$name.png")
    }

    @Test fun walnutPortraitLamp() = shoot(TableSurface.WALNUT, false, false, false)
    @Test fun walnutPortraitFire() = shoot(TableSurface.WALNUT, true, false, false)
    @Test fun feltPortraitFire() = shoot(TableSurface.GREEN_MAT, true, false, false)
    @Test fun walnutPortraitLampBare() = shoot(TableSurface.WALNUT, false, false, true)
    @Test fun walnutPortraitFireBare() = shoot(TableSurface.WALNUT, true, false, true)
    @Test fun feltPortraitFireBare() = shoot(TableSurface.GREEN_MAT, true, false, true)

    @Test @Config(qualifiers = "w952dp-h427dp-land-360dpi")
    fun walnutLandscapeLamp() = shoot(TableSurface.WALNUT, false, true, false)
    @Test @Config(qualifiers = "w952dp-h427dp-land-360dpi")
    fun walnutLandscapeFire() = shoot(TableSurface.WALNUT, true, true, false)
    @Test @Config(qualifiers = "w952dp-h427dp-land-360dpi")
    fun feltLandscapeFire() = shoot(TableSurface.GREEN_MAT, true, true, false)
    @Test @Config(qualifiers = "w952dp-h427dp-land-360dpi")
    fun walnutLandscapeLampBare() = shoot(TableSurface.WALNUT, false, true, true)
    @Test @Config(qualifiers = "w952dp-h427dp-land-360dpi")
    fun walnutLandscapeFireBare() = shoot(TableSurface.WALNUT, true, true, true)
    @Test @Config(qualifiers = "w952dp-h427dp-land-360dpi")
    fun feltLandscapeFireBare() = shoot(TableSurface.GREEN_MAT, true, true, true)
}
