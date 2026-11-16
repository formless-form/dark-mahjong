package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.TileSet
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
 * Silk brocade table, the daily puzzles' monthly reward (Gareth's pick, 2026-11-15, mock-up 10):
 * a board, and zoomed on the cloth under the lamp. build/brocade/brocade.png, brocade-zoomed.png.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-port-xxhdpi")
class BrocadePreview {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(zoomed: Boolean = false, surface: TableSurface = TableSurface.BROCADE, tiles: TileSet = TileSet.IVORY, name: String = "brocade") {
        TileWear.prepareNow()
        val n = ConstellationRepository.levelIndexOf("pillar_of_heaven")!!
        // Zoomed: only a few tiles, so the cloth shows
        val state = MahjongGameEngine.startNewGame(ConstellationRepository.getLevel(n))
            .let { st -> if (zoomed) st.copy(tiles = st.tiles.mapIndexed { k, t -> t.copy(isRemoved = k >= 6) }) else st }
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state,
                    levelIndex = n,
                    settings = GameSettings(tableSurface = surface, tileSet = tiles, fireplaceLit = false),
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        if (zoomed) {
            compose.waitForIdle()
            compose.onRoot().performTouchInput {
                val c = Offset(width * 0.5f, height * 0.55f)
                pinch(c - Offset(40f, 0f), c - Offset(300f, 0f), c + Offset(40f, 0f), c + Offset(300f, 0f), durationMillis = 400)
            }
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage("build/brocade/$name${if (zoomed) "-zoomed" else ""}.png")
    }

    @Test fun table() = shoot()

    @Test fun tableZoomed() = shoot(zoomed = true)
}
