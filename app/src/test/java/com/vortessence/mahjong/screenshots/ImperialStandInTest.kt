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
 * Imperial's images come with the Play build only; with or without them an Imperial tile must
 * draw (stamped from the images, or in the set's stand-in colours).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImperialStandInTest {
    @Test
    fun `an Imperial tile draws with or without its images`() {
        ImperialImages.assets = RuntimeEnvironment.getApplication().assets
        // Present in Play, absent in open-source: either is right, if the quick look agrees with loading
        assertTrue(ImperialImages.present() == (ImperialImages.load() != null))
        val w = 120
        val h = 190
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            TileRenderer.drawSample(this, Tile(TileSuit.CHARACTER, 5), 4f, 4f, 100f, 133f, TileKits.IMPERIAL, look = 3)
        }
        // The middle of the face is not clear
        assertTrue(bitmap.getPixel(54, 70) ushr 24 > 0)
    }
}
