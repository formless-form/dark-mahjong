package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.BoardSolvabilityGenerator
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.ui.components.LampColour
import com.vortessence.mahjong.ui.components.PerfSwitches
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
 * The lamp at several colour temperatures (LampColour; 2000 K chosen), on the same deal: the green mat and walnut
 * with the fireplace lit, and the green mat by the lamp alone. Rendered into
 * build/lamp-colour-preview/<kelvin>/. Run: ./gradlew testFossDebugUnitTest --tests '*LampColourPreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class LampColourPreview {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun restore() {
        LampColour.kelvin = LampColour.DEFAULT_KELVIN
    }

    private fun shoot(surface: TableSurface, fireplace: Boolean, kelvin: Float) {
        LampColour.kelvin = kelvin
        PerfSwitches.off = emptySet()
        TileWear.prepareNow()
        TileRenderer.TileAtlas.bakeInline = true
        val constellation = ConstellationRepository.getLevel(18)
        val tiles = MahjongGameEngine.recalculateFreedom(BoardSolvabilityGenerator.generateSolvableBoard(constellation, Random(7)))
        val state = MahjongGameEngine.startNewGame(constellation).copy(tiles = tiles)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state,
                    levelIndex = 18,
                    settings = GameSettings(tableSurface = surface, fireplaceLit = fireplace),
                    winMessage = null,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        val name = surface.name.lowercase() + if (fireplace) "-fire" else "-lamp"
        compose.onRoot().captureRoboImage("build/lamp-colour-preview/${kelvin.toInt()}/$name.png")
    }

    @Test fun feltFire2700() = shoot(TableSurface.GREEN_MAT, true, 2700f)
    @Test fun feltFire2400() = shoot(TableSurface.GREEN_MAT, true, 2400f)
    @Test fun feltFire2100() = shoot(TableSurface.GREEN_MAT, true, 2100f)
    @Test fun feltFire2000() = shoot(TableSurface.GREEN_MAT, true, 2000f)
    @Test fun feltFire1800() = shoot(TableSurface.GREEN_MAT, true, 1800f)
    @Test fun walnutFire2700() = shoot(TableSurface.WALNUT, true, 2700f)
    @Test fun walnutFire2400() = shoot(TableSurface.WALNUT, true, 2400f)
    @Test fun walnutFire2100() = shoot(TableSurface.WALNUT, true, 2100f)
    @Test fun walnutFire2000() = shoot(TableSurface.WALNUT, true, 2000f)
    @Test fun walnutFire1800() = shoot(TableSurface.WALNUT, true, 1800f)
    @Test fun feltLamp2700() = shoot(TableSurface.GREEN_MAT, false, 2700f)
    @Test fun feltLamp2400() = shoot(TableSurface.GREEN_MAT, false, 2400f)
    @Test fun feltLamp2100() = shoot(TableSurface.GREEN_MAT, false, 2100f)
    @Test fun feltLamp2000() = shoot(TableSurface.GREEN_MAT, false, 2000f)
    @Test fun feltLamp1800() = shoot(TableSurface.GREEN_MAT, false, 1800f)
}
