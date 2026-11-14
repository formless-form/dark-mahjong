package com.vortessence.mahjong.screenshots

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.vortessence.mahjong.ui.components.ImperialImages
import com.vortessence.mahjong.ui.components.SampleStrip
import com.vortessence.mahjong.ui.components.TileKits
import com.vortessence.mahjong.ui.components.TileWear
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Makes Imperial's ready-made picture for Settings from the Imperial images:
 * build/imperial-samples/settings-samples.png. It ships as the Imperial images'
 * settings-samples.webp, so Settings never loads the whole set. Make it again whenever
 * the images change. Needs the images, so it runs in the Play build only:
 *   ./gradlew testPlayDebugUnitTest --tests '*ImperialSettingsSamples*'
 *   then convert the PNG to WebP (quality 92) into the images' folder
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImperialSettingsSamples {
    @Test
    fun make() {
        ImperialImages.assets = RuntimeEnvironment.getApplication().assets
        assumeTrue("The Imperial images are not in this build", ImperialImages.load() != null)
        TileWear.prepareNow()
        val picture = SampleStrip.make(TileKits.IMPERIAL, SampleStrip.IMPERIAL_TILES).asAndroidBitmap()
        val out = File("build/imperial-samples/settings-samples.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
