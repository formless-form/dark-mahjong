package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.BoardSolvabilityGenerator
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.engine.TutorialStep
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
 * Fixed deals of big layouts at a fixed moment of the fire, Pixel 9 Pro size, to compare the look
 * across rendering changes. build/perf-preview/<set> (perfSet, default "current").
 * Run: ./gradlew testFossDebugUnitTest --tests '*PerfPreview*' [-PperfSet=name] [-PperfOff=stage,stage]
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class PerfPreview {
    @get:Rule
    val compose = createComposeRule()

    private val set = System.getProperty("perfSet") ?: "current"

    private fun shoot(
        layout: String,
        surface: TableSurface,
        fireplace: Boolean,
        name: String,
        off: Set<String> = emptySet(),
        // A selected tile, a hinted pair and the tutorial's ring round every free tile
        highlights: Boolean = false,
        // Every tile taken away, as after a win
        cleared: Boolean = false,
    ) {
        PerfSwitches.off = off + (System.getProperty("perfOff")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList())
        TileWear.prepareNow()
        TileRenderer.TileAtlas.bakeInline = true
        val constellation = ConstellationRepository.constellations.first { it.name == layout }
        var tiles = MahjongGameEngine.recalculateFreedom(
            BoardSolvabilityGenerator.generateSolvableBoard(constellation, Random(7)),
        )
        if (cleared) tiles = tiles.map { it.copy(isRemoved = true, isFree = false) }
        var state = MahjongGameEngine.startNewGame(constellation).copy(tiles = tiles)
        val free = tiles.filter { it.isFree }.map { it.id }
        if (highlights) state = state.copy(selectedTileId = free[0], hintPair = free[1] to free[2])
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state,
                    levelIndex = null,
                    settings = GameSettings(tableSurface = surface, fireplaceLit = fireplace),
                    tutorialStep = if (highlights) TutorialStep("", "", highlightFreeTiles = true) else null,
                    winMessage = null,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/perf-preview/$set/$name.png")
    }

    @Test fun quadElmFire() = shoot("Quad", TableSurface.ELM_WOOD, true, "quad-elm-fire")
    @Test fun quadStrawFire() = shoot("Quad", TableSurface.THAI_STRAW, true, "quad-straw-fire")
    @Test fun quadFeltLamp() = shoot("Quad", TableSurface.GREEN_MAT, false, "quad-felt-lamp")
    @Test fun platformElmFire() = shoot("Platform", TableSurface.ELM_WOOD, true, "platform-elm-fire")
    @Test fun quadSlateFire() = shoot("Quad", TableSurface.SLATE, true, "quad-slate-fire")
    @Test fun quadElmHighlights() = shoot("Quad", TableSurface.ELM_WOOD, true, "quad-elm-highlights", highlights = true)
    @Test fun quadFeltHighlightsLamp() = shoot("Quad", TableSurface.GREEN_MAT, false, "quad-felt-highlights-lamp", highlights = true)
    @Test fun clearedElmFire() = shoot("Quad", TableSurface.ELM_WOOD, true, "cleared-elm-fire", cleared = true)

    // Drawn shape by shape rather than from the tile atlas, to compare
    @Test fun quadElmFireLive() = shoot("Quad", TableSurface.ELM_WOOD, true, "quad-elm-fire-live", setOf("atlas"))
    @Test fun quadFeltLampLive() = shoot("Quad", TableSurface.GREEN_MAT, false, "quad-felt-lamp-live", setOf("atlas"))
}
