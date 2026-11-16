package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.BrocadeProgress
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.TileSet
import com.vortessence.mahjong.ui.components.ImperialImages
import com.vortessence.mahjong.ui.components.MenuFire
import com.vortessence.mahjong.ui.components.TileKits
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.screens.ImperialProgress
import com.vortessence.mahjong.ui.screens.SettingsScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.YearMonth

/**
 * Settings' tile-set choice, as sample tiles or a plain switch, on a screen tall enough for it all:
 * build/tile-set-settings/<name>.png.
 * Run: ./gradlew testFossDebugUnitTest --tests '*TileSetSettingsPreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h1900dp-port-360dpi")
class TileSetSettingsPreview {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(
        samples: Boolean, set: TileSet, name: String, imperial: ImperialProgress? = null,
        brocade: BrocadeProgress? = null, surface: TableSurface = TableSurface.GREEN_MAT,
    ) {
        MenuFire.flickers = false
        TileWear.prepareNow()
        compose.setContent {
            MahjongTheme {
                SettingsScreen(
                    settings = GameSettings(tileSet = set, tableSurface = surface), onSettingsChanged = {}, onBack = {}, tileSamples = samples,
                    imperial = imperial, brocade = brocade,
                )
            }
        }
        compose.waitForIdle()
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
                    settings = GameSettings(tileSet = TileSet.CHARCOAL, fireplaceLit = true, tableSurface = com.vortessence.mahjong.data.model.TableSurface.ELM_WOOD),
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

    // Imperial (Play build only, skipped elsewhere): locked partway through the Journey, and earned and chosen
    private fun imperial(): Boolean {
        ImperialImages.assets = RuntimeEnvironment.getApplication().assets
        return ImperialImages.available && ImperialImages.load() != null
    }

    @Test @Config(qualifiers = "w427dp-h2600dp-port-360dpi")
    fun imperialLocked() {
        assumeTrue("The Imperial images are not in this build", imperial())
        shoot(true, TileSet.JADE, "imperial-locked", ImperialProgress(36, 100))
    }

    @Test @Config(qualifiers = "w427dp-h2600dp-port-360dpi")
    fun imperialEarned() {
        assumeTrue("The Imperial images are not in this build", imperial())
        shoot(true, TileSet.IMPERIAL, "imperial-earned", ImperialProgress(100, 100))
    }

    // Open-source build: no images, no Imperial card, and a stored Imperial draws Ivory
    @Test
    fun imperialAbsentFallsBackToIvory() {
        ImperialImages.assets = null
        assertFalse(ImperialImages.available)
        assertEquals(TileKits.IVORY, TileKits.of(TileSet.IMPERIAL))
    }

    // Brocade: on track, a day missed, earned and chosen; taller, so its card under the tables shows
    @Test @Config(qualifiers = "w427dp-h2600dp-port-360dpi")
    fun brocadeLocked() = shoot(true, TileSet.IVORY, "brocade-locked", brocade = BrocadeProgress(false, 14, 30, YearMonth.of(2026, 11), false))

    @Test @Config(qualifiers = "w427dp-h2600dp-port-360dpi")
    fun brocadeMissed() = shoot(true, TileSet.IVORY, "brocade-missed", brocade = BrocadeProgress(false, 12, 30, YearMonth.of(2026, 11), true))

    @Test @Config(qualifiers = "w427dp-h2600dp-port-360dpi")
    fun brocadeEarned() = shoot(
        true, TileSet.IVORY, "brocade-earned",
        brocade = BrocadeProgress(true, 3, 31, YearMonth.of(2026, 12), false), surface = TableSurface.BROCADE,
    )
}
