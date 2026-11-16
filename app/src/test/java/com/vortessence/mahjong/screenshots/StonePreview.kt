package com.vortessence.mahjong.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.ui.components.SlateBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The menus' slate, unlit (the firelight never lets the test idle). The JVM can't bake the slate,
 * so this only checks its shaders compile and the page falls back to plain dark; see it on a phone.
 * Run: ./gradlew testFossDebugUnitTest --tests '*StonePreview*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class StonePreview {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun slate() {
        compose.setContent { SlateBackdrop(Modifier.fillMaxSize(), fire = false) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/stone_slate.png")
    }
}
