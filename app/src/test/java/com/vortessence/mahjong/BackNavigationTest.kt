package com.vortessence.mahjong

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.ui.components.MenuFire
import com.vortessence.mahjong.ui.viewmodel.MahjongViewModel
import com.vortessence.mahjong.ui.viewmodel.Screen
import com.vortessence.mahjong.update.AppUpdates
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Back from a game goes to where it was started from, by the game's own back button
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun backFromGame(start: (MahjongViewModel) -> Unit): Screen {
        // The menus' firelight never settles: the clock is stepped by hand
        MenuFire.flickers = false
        compose.mainClock.autoAdvance = false
        val viewModel = MahjongViewModel(compose.activity.application)
        val updates = AppUpdates(compose.activity)
        compose.setContent { MahjongApp(viewModel, updates) }
        compose.runOnUiThread { start(viewModel) }
        compose.mainClock.advanceTimeBy(2000)
        compose.onAllNodesWithContentDescription("Back")[0].performClick()
        compose.mainClock.advanceTimeBy(2000)
        return viewModel.currentScreen.value
    }

    @Test
    fun fromLayouts() {
        val screen = backFromGame { it.navigateTo(Screen.ConstellationSelect); it.startConstellation(ConstellationRepository.getById("mah_828570775")!!) }
        assertEquals(Screen.ConstellationSelect, screen)
    }

    @Test
    fun fromLevels() {
        val screen = backFromGame { it.navigateTo(Screen.LevelSelect); it.startLevel(3) }
        assertEquals(Screen.LevelSelect, screen)
    }

    @Test
    fun fromTodaysPuzzle() {
        val screen = backFromGame { it.navigateTo(Screen.TodaysPuzzle); it.startTodaysPuzzle() }
        assertEquals(Screen.TodaysPuzzle, screen)
    }
}
