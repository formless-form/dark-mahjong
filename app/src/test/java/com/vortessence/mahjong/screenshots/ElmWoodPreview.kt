package com.vortessence.mahjong.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.ui.components.FlameLight
import com.vortessence.mahjong.ui.components.TableCamera
import com.vortessence.mahjong.ui.components.TableSurfaceRenderer
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Elm wood table (Gareth's pick, 2026-11-15, mock-up 24): bare, whole and zoomed in, by lamp and
 * fire, and with a board. build/elm-wood/table-*.png, board-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-port-xxhdpi")
class ElmWoodPreview {
    @get:Rule
    val compose = createComposeRule()

    // The JVM can't read the surface map back from the GPU: drawn on the CPU so the relief shows
    @Before
    fun reliefOn() {
        TableSurfaceRenderer.softwareRelief = true
    }

    @After
    fun reliefOff() {
        TableSurfaceRenderer.softwareRelief = false
    }

    private fun table(zoomed: Boolean, fire: Boolean) {
        compose.setContent { Table(zoomed, fire) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/elm-wood/table-${if (zoomed) "zoom" else "whole"}-${if (fire) "fire" else "lamp"}.png")
    }

    // The table as the board draws it, bare: colour, surface map, then lamp and fire light
    @Composable
    private fun Table(zoomed: Boolean, fire: Boolean) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val cache = TableSurfaceRenderer.AlbedoCache(rememberGraphicsLayer())
        val mapLayer = rememberGraphicsLayer()
        var area by remember { mutableStateOf(IntSize.Zero) }
        var relief by remember { mutableStateOf<TableSurfaceRenderer.Relief?>(null) }
        // As far as a pinch zooms, about the middle
        val zoom = if (zoomed) TableCamera.MAX_ZOOM else 1f
        val pan = Offset(area.width * 0.5f * (1f - zoom), area.height * 0.5f * (1f - zoom))
        val w = area.width.toFloat()
        val h = area.height.toFloat()
        // Usual tile width: the wood is life-size against it
        val tile = h * TILE_SHARE
        LaunchedEffect(area) {
            if (area != IntSize.Zero) {
                relief = TableSurfaceRenderer.bakeRelief(SURFACE, mapLayer, density, direction, area, w, h, zoom, pan, tile)
            }
        }
        Canvas(Modifier.fillMaxSize().onSizeChanged { area = it }) {
            if (area == IntSize.Zero) return@Canvas
            TableSurfaceRenderer.drawTableAlbedo(this, cache, SURFACE, w, h, null, zoom, pan, tile = tile)
            // A moment into the fire, at the settings' defaults
            val flame = FlameLight.at(1.5f, 0.5f, fire, 0.6f)
            drawContext.canvas.save()
            drawContext.canvas.translate(pan.x, pan.y)
            drawContext.canvas.scale(zoom, zoom)
            TableSurfaceRenderer.drawTableLight(this, SURFACE, w, h, zoom, pan, flame, shadowMap = null, fireShadows = false, relief = relief)
            drawContext.canvas.restore()
        }
    }

    private fun board(fire: Boolean) {
        TileWear.prepareNow()
        val n = ConstellationRepository.levelIndexOf("pillar_of_heaven")!!
        // Firelight never settles: the clock is stepped by hand
        if (fire) compose.mainClock.autoAdvance = false
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = MahjongGameEngine.startNewGame(ConstellationRepository.getLevel(n)),
                    levelIndex = n,
                    settings = GameSettings(tableSurface = SURFACE, fireplaceLit = fire),
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        if (fire) compose.mainClock.advanceTimeBy(1500) else compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/elm-wood/board-${if (fire) "fire" else "lamp"}.png")
    }

    @Test fun tableByTheLamp() = table(zoomed = false, fire = false)

    @Test fun tableByTheFire() = table(zoomed = false, fire = true)

    @Test fun tableZoomedByTheLamp() = table(zoomed = true, fire = false)

    @Test fun tableZoomedByTheFire() = table(zoomed = true, fire = true)

    @Test fun boardByTheLamp() = board(fire = false)

    @Test fun boardByTheFire() = board(fire = true)

    private companion object {
        val SURFACE = TableSurface.ELM_WOOD

        // Tile width as a share of table height on a usual board
        const val TILE_SHARE = 0.04f
    }
}
