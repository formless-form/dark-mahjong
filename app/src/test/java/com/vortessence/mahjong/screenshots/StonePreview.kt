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
 * The menu screens' slate background (the firelight's clock never lets the test go idle, so
 * without it). The JVM draws in software, where the slate can't be baked, so this checks that
 * its shaders compile and the page falls back to plain dark; see it on a phone.
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
