package com.vortessence.mahjong.screenshots

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TileSuit
import com.vortessence.mahjong.ui.components.LampColour
import com.vortessence.mahjong.ui.components.TileKit
import com.vortessence.mahjong.ui.components.TileKits
import com.vortessence.mahjong.ui.components.TileRenderer
import com.vortessence.mahjong.ui.components.TileWear
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Every face of a tile set on one sheet, a row per suit, under the lamp's colour:
 * build/face-sheets/<set>.png. Run: ./gradlew testFossDebugUnitTest --tests '*FaceSheet*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FaceSheet {
    private fun sheet(kit: TileKit, name: String, tileW: Float = 90f) {
        TileWear.prepareNow()
        val tileH = tileW * 1.33f
        val gap = tileW * 0.25f
        val cols = 9
        val rows = TileSuit.entries.size
        val w = (cols * (tileW + gap) + gap).toInt()
        val h = (rows * (tileH + gap) + gap).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val lamp = LampColour.tiles().let { Color(it[0], it[1], it[2]) }
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color(0xFF3A2416))
            TileSuit.entries.forEachIndexed { r, suit ->
                for (v in 1..9) {
                    val tile = Tile(suit, v)
                    if (TileRenderer.getTileVector(tile) == null) continue
                    TileRenderer.drawSample(this, tile, gap + (v - 1) * (tileW + gap), gap + r * (tileH + gap), tileW, tileH, kit, look = v + r)
                }
            }
            drawRect(lamp, blendMode = BlendMode.Modulate)
        }
        File("build/face-sheets").mkdirs()
        FileOutputStream("build/face-sheets/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun ivory() = sheet(TileKits.IVORY, "ivory")
    @Test fun charcoal() = sheet(TileKits.CHARCOAL, "charcoal")
}
