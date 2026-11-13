package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.LevelRecord
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.ui.components.MenuFire
import com.vortessence.mahjong.ui.screens.LevelSelectScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Journey (the levels) with each cleared level's best clear, over sample records: perfect,
 * clean with undos, helped, and cleared before records were kept. Into build/journey/journey.png. Run: ./gradlew testFossDebugUnitTest --tests '*JourneyPreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class JourneyPreview {
    @get:Rule
    val compose = createComposeRule()

    private val ids = ConstellationRepository.levelConstellations.map { it.id }
    private val progress = Progress(
        highestLevelReached = 7,
        completedConstellations = ids.take(6).toSet() + ConstellationRepository.TUTORIAL_ID,
        cleanConstellations = setOf(ids[0], ids[1], ids[3], ids[4]),
        records = mapOf(
            ids[0] to LevelRecord(135_000, 0, 0, 0),
            ids[1] to LevelRecord(242_000, 0, 0, 3),
            ids[2] to LevelRecord(527_000, 1, 2, 5),
            // ids[3]: cleared cleanly before records were kept
            ids[4] to LevelRecord(390_000, 0, 0, 0),
            ids[5] to LevelRecord(725_000, 2, 0, 0),
        ),
    )

    private fun shoot(name: String) {
        MenuFire.flickers = false
        compose.setContent {
            MahjongTheme {
                LevelSelectScreen(
                    progress = progress, onLevelSelected = {}, onBack = {},
                    openAt = 0,
                )
            }
        }
        compose.onRoot().captureRoboImage("build/journey/$name.png")
    }

    @Test fun journey() = shoot("journey")
}
