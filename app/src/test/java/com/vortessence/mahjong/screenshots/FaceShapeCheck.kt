package com.vortessence.mahjong.screenshots

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TileSuit
import com.vortessence.mahjong.ui.components.TileRenderer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Faces told apart only by colour: each face drawn in one colour (as a single-colour tile set
 * would), and every pair compared. Pairs whose shapes nearly coincide are written to
 * build/face-shapes.txt, with how much of their shape they share.
 * Run: ./gradlew testFossDebugUnitTest --tests '*FaceShapeCheck*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FaceShapeCheck {
    private val w = 150
    private val h = 200

    private fun mask(tile: Tile): BooleanArray? {
        val vector = TileRenderer.getTileVector(tile) ?: return null
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            TileRenderer.drawVectorTile(this, vector, w / 2f, h / 2f, w * 0.85f, h * 0.85f, tint = Color.White)
        }
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        return BooleanArray(px.size) { (px[it] ushr 24) > 100 }
    }

    @Test
    fun check() {
        val faces = TileSuit.entries.flatMap { suit -> (1..9).map { Tile(suit, it) } }
            .mapNotNull { t -> mask(t)?.let { t to it } }
        val lines = mutableListOf<String>()
        for (i in faces.indices) for (j in i + 1 until faces.size) {
            val (a, ma) = faces[i]
            val (b, mb) = faces[j]
            var both = 0
            var either = 0
            for (k in ma.indices) {
                if (ma[k] && mb[k]) both++
                if (ma[k] || mb[k]) either++
            }
            val shared = both.toFloat() / either
            if (shared > 0.5f) lines += "%.3f  %s %d  ~  %s %d".format(shared, a.suit, a.value, b.suit, b.value)
        }
        File("build/face-shapes.txt").writeText("faces ${faces.size}\n" + lines.sortedDescending().joinToString("\n") + "\n")
    }
}
