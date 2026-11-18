package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
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
 * Lamp alone and with the fireplace on each table surface, at Pixel 9 Pro size.
 * build/lighting-preview. Run: ./gradlew testFossDebugUnitTest --tests '*LightingPreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class LightingPreview {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(surface: TableSurface, fireplace: Boolean, lamp: Float = 0.5f, fire: Float = 0.5f, name: String? = null) {
        TileWear.prepareNow()
        // Firelight never settles: the clock is stepped by hand
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = MahjongGameEngine.startNewGame(ConstellationRepository.getLevel(18)),
                    levelIndex = 18,
                    settings = GameSettings(tableSurface = surface, fireplaceLit = fireplace, lampBrightness = lamp, fireIntensity = fire),
                    winMessage = null,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/lighting-preview/${surface.name.lowercase()}-${name ?: if (fireplace) "fireplace" else "lamp"}.png")
    }

    @Test fun elmLamp() = shoot(TableSurface.ELM_WOOD, false)
    @Test fun elmFireplace() = shoot(TableSurface.ELM_WOOD, true)
    @Test fun greenMatLamp() = shoot(TableSurface.GREEN_MAT, false)
    @Test fun greenMatFireplace() = shoot(TableSurface.GREEN_MAT, true)
    @Test fun strawLamp() = shoot(TableSurface.THAI_STRAW, false)
    @Test fun strawFireplace() = shoot(TableSurface.THAI_STRAW, true)
    @Test fun elmDimLamp() = shoot(TableSurface.ELM_WOOD, false, lamp = 0f, name = "lamp-dim")
    @Test fun elmBrightLamp() = shoot(TableSurface.ELM_WOOD, false, lamp = 1f, name = "lamp-bright")
    @Test fun elmDimLampFire() = shoot(TableSurface.ELM_WOOD, true, lamp = 0f, name = "fireplace-dim-lamp")
    @Test fun elmBlaze() = shoot(TableSurface.ELM_WOOD, true, lamp = 1f, fire = 1f, name = "blaze")
}
