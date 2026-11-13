package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.ui.components.LampColour
import com.vortessence.mahjong.ui.components.MenuFire
import com.vortessence.mahjong.ui.screens.LevelSelectScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The menus' slate by the lamp at a few colour temperatures (6600 K is close to the white lamp
 * the menus had before), into build/menu-lamp/levels-<kelvin>.png.
 * Run: ./gradlew testFossDebugUnitTest --tests '*MenuLampPreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class MenuLampPreview {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun back() {
        LampColour.kelvin = LampColour.DEFAULT_KELVIN
    }

    private fun levels(kelvin: Float) {
        MenuFire.flickers = false
        LampColour.kelvin = kelvin
        compose.setContent {
            MahjongTheme {
                LevelSelectScreen(
                    progress = Progress(
                        highestLevelReached = 4,
                        completedConstellations = ConstellationRepository.levelConstellations.take(3).map { it.id }.toSet() + "tutorial",
                    ),
                    levels = ConstellationRepository.levelConstellations,
                    onLevelSelected = {},
                    onBack = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/menu-lamp/levels-${if (kelvin == LampColour.DEFAULT_KELVIN) "menu-${LampColour.MENU_KELVIN.toInt()}" else kelvin.toInt().toString()}.png")
    }

    @Test fun white() = levels(6600f)
    @Test fun at2000() = levels(2001f)
    @Test fun menuDefault() = levels(LampColour.DEFAULT_KELVIN)
}
