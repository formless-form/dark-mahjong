package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TableLighting
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The lamp alone, with the candle, and with the fireplace, side by side on each table surface, at the Pixel 9 Pro's size.
 * Rendered into build/lighting-preview. Run: ./gradlew testDebugUnitTest --tests '*LightingPreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class LightingPreview {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(surface: TableSurface, lighting: TableLighting) {
        TileWear.prepareNow()
        // Candles animate for ever, so the clock is stepped by hand rather than run to idle
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = MahjongGameEngine.startNewGame(ConstellationRepository.getLevel(18)),
                    levelIndex = 18,
                    settings = GameSettings(tableSurface = surface, lighting = lighting),
                    winMessage = null,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onChooseConstellation = {}, onMainMenu = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/lighting-preview/${surface.name.lowercase()}-${lighting.name.lowercase()}.png")
    }

    @Test fun walnutLamp() = shoot(TableSurface.WALNUT, TableLighting.LAMP)
    @Test fun walnutCandles() = shoot(TableSurface.WALNUT, TableLighting.CANDLE)
    @Test fun walnutFireplace() = shoot(TableSurface.WALNUT, TableLighting.FIREPLACE)
    @Test fun greenMatLamp() = shoot(TableSurface.GREEN_MAT, TableLighting.LAMP)
    @Test fun greenMatCandles() = shoot(TableSurface.GREEN_MAT, TableLighting.CANDLE)
    @Test fun greenMatFireplace() = shoot(TableSurface.GREEN_MAT, TableLighting.FIREPLACE)
    @Test fun strawLamp() = shoot(TableSurface.THAI_STRAW, TableLighting.LAMP)
    @Test fun strawCandles() = shoot(TableSurface.THAI_STRAW, TableLighting.CANDLE)
    @Test fun strawFireplace() = shoot(TableSurface.THAI_STRAW, TableLighting.FIREPLACE)
}
