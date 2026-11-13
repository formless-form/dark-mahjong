package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TileSet
import com.vortessence.mahjong.ui.components.MenuFire
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.screens.SettingsScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings with the choice of tile sets, as sample tiles or as a plain switch, on a screen tall
 * enough to show it all: build/tile-set-settings/<name>.png.
 * Run: ./gradlew testFossDebugUnitTest --tests '*TileSetSettingsPreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h1900dp-port-360dpi")
class TileSetSettingsPreview {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(samples: Boolean, set: TileSet, name: String) {
        MenuFire.flickers = false
        TileWear.prepareNow()
        compose.setContent {
            MahjongTheme {
                SettingsScreen(settings = GameSettings(tileSet = set), onSettingsChanged = {}, onBack = {}, tileSamples = samples)
            }
        }
        compose.onRoot().captureRoboImage("build/tile-set-settings/$name.png")
    }

    // Chosen in Settings, the set reaches the board
    @Test
    fun boardInCharcoal() {
        TileWear.prepareNow()
        com.vortessence.mahjong.ui.components.TileRenderer.TileAtlas.bakeInline = true
        val layout = com.vortessence.mahjong.data.repository.ConstellationRepository.getById("mah_773823936")!!
        val state = com.vortessence.mahjong.engine.MahjongGameEngine.startNewGame(layout, kotlin.random.Random(3))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MahjongTheme {
                com.vortessence.mahjong.ui.screens.GameScreen(
                    gameState = state, levelIndex = null,
                    settings = GameSettings(tileSet = TileSet.CHARCOAL, fireplaceLit = true, tableSurface = com.vortessence.mahjong.data.model.TableSurface.WALNUT),
                    winMessage = null,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/tile-set-settings/board-charcoal.png")
    }

    @Test fun samplesCharcoal() = shoot(true, TileSet.CHARCOAL, "samples-charcoal")
    @Test fun samplesIvory() = shoot(true, TileSet.IVORY, "samples-ivory")
    @Test fun switchCharcoal() = shoot(false, TileSet.CHARCOAL, "switch-charcoal")
}
