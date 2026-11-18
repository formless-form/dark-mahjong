package com.vortessence.mahjong.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.LevelRecord
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.TileSet
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.ui.components.ImperialImages
import com.vortessence.mahjong.ui.components.NameSeal
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.screens.LevelSelectScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Scenes for the Play listing's designed images, by firelight: build/listing/. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-port-xxhdpi")
class ListingScenes {
    @get:Rule
    val compose = createComposeRule()

    private fun board(
        name: String, layout: String = "pillar_of_heaven", surface: TableSurface = TableSurface.ELM_WOOD,
        tiles: TileSet = TileSet.IVORY, fire: Boolean = true, intensity: Float = 1f, zoom: Float = 0f, keep: Int = Int.MAX_VALUE, fireMs: Long = 800,
    ) {
        TileWear.prepareNow()
        if (tiles == TileSet.IMPERIAL) {
            ImperialImages.assets = RuntimeEnvironment.getApplication().assets
            ImperialImages.load()
        }
        val n = ConstellationRepository.levelIndexOf(layout)
        val level = n?.let { ConstellationRepository.getLevel(it) } ?: ConstellationRepository.getById(layout)!!
        val state = MahjongGameEngine.startNewGame(level)
            .let { st -> st.copy(tiles = st.tiles.mapIndexed { k, t -> t.copy(isRemoved = k >= keep) }) }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state, levelIndex = n,
                    settings = GameSettings(tableSurface = surface, tileSet = tiles, fireplaceLit = fire, fireIntensity = intensity),
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(fireMs)
        if (zoom > 0f) {
            compose.onRoot().performTouchInput {
                val c = Offset(width * 0.5f, height * 0.5f)
                pinch(c - Offset(40f, 0f), c - Offset(40f + zoom, 0f), c + Offset(40f, 0f), c + Offset(40f + zoom, 0f), durationMillis = 400)
            }
            compose.mainClock.advanceTimeBy(1200)
        }
        compose.onRoot().captureRoboImage("build/listing/$name.png")
    }

    @Test fun fireIvoryElm() = board("fire-ivory-elm")
    @Test fun fireIvoryElmZoomed() = board("fire-ivory-elm-zoomed", zoom = 260f)

    // The Journey: best clears with times and help, gold ribbons on the clean ones
    @Test @Config(qualifiers = "w427dp-h952dp-port-360dpi")
    fun journey() {
        com.vortessence.mahjong.ui.components.MenuFire.flickers = false
        val levels = ConstellationRepository.levelConstellations
        val records = listOf(
            LevelRecord(184_000, 0, 0, 0), LevelRecord(242_000, 0, 0, 2), LevelRecord(301_000, 0, 0, 0),
            LevelRecord(356_000, 1, 0, 3), LevelRecord(287_000, 0, 0, 0), LevelRecord(412_000, 0, 1, 1),
            LevelRecord(398_000, 0, 0, 0), LevelRecord(465_000, 0, 0, 4), LevelRecord(521_000, 0, 0, 0),
        )
        val done = levels.take(records.size)
        val progress = Progress(
            highestLevelReached = records.size + 1,
            completedConstellations = done.map { it.id }.toSet() + "tutorial",
            cleanConstellations = done.zip(records).filter { it.second.clean }.map { it.first.id }.toSet(),
            hints = 3,
            records = done.zip(records).associate { (c, r) -> c.id to r },
        )
        compose.setContent {
            MahjongTheme { LevelSelectScreen(progress = progress, levels = levels, onLevelSelected = {}, onBack = {}) }
        }
        compose.mainClock.advanceTimeBy(1500)
        // Up the list, so the cleared levels fill the screen
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Level 2"))
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().captureRoboImage("build/listing/journey.png")
    }

    // The name seal alone, large: for the feature graphic
    @Test fun seal() {
        compose.setContent {
            // On pure green, keyed out afterwards
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF00FF00)).padding(androidx.compose.ui.unit.Dp(8f)),
            ) { NameSeal(size = androidx.compose.ui.unit.Dp(120f)) }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/listing/seal.png")
    }
}
