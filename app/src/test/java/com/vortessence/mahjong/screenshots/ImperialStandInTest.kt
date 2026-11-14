package com.vortessence.mahjong.screenshots

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TileSuit
import com.vortessence.mahjong.ui.components.ImperialImages
import com.vortessence.mahjong.ui.components.TileKits
import com.vortessence.mahjong.ui.components.TileRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Imperial is drawn from images that come with the Play build only. Whether or not a build has
 * them, drawing an Imperial tile must never fail: with them it is stamped from the images,
 * without them from the set's stand-in colours.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImperialStandInTest {
    @Test
    fun `an Imperial tile draws with or without its images`() {
        ImperialImages.assets = RuntimeEnvironment.getApplication().assets
        // Present in the Play build's tests, absent in the open-source build's: both are right,
        // and the quick look agrees with what loading finds
        assertTrue(ImperialImages.present() == (ImperialImages.load() != null))
        val w = 120
        val h = 190
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            TileRenderer.drawSample(this, Tile(TileSuit.CHARACTER, 5), 4f, 4f, 100f, 133f, TileKits.IMPERIAL, look = 3)
        }
        // Something was drawn: the middle of the face is not clear
        assertTrue(bitmap.getPixel(54, 70) ushr 24 > 0)
    }
}
