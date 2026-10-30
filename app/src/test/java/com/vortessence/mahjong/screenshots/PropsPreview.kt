package com.vortessence.mahjong.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.ui.components.MenuFire
import com.vortessence.mahjong.ui.components.SlateBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The menus' slate by firelight, without and with the bamboo standing between the hearth and the
 * screen, casting its long shadow across it, at the Pixel's size. Rendered into
 * build/props-preview.
 * Run: ./gradlew testFossDebugUnitTest --tests '*PropsPreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class PropsPreview {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(props: Int, name: String) {
        MenuFire.flickers = false
        compose.setContent { SlateBackdrop(Modifier.fillMaxSize(), fireIntensity = 0.6f, props = props) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/props-preview/$name.png")
    }

    @Test fun none() = shoot(0, "0-none")
    @Test fun bamboo() = shoot(1, "1-bamboo")
}
