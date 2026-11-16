package com.vortessence.mahjong.ui.components

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.TileSet
import kotlinx.coroutines.delay

/**
 * Pre-renders Settings' sample strips and table swatches soon after launch so Settings opens
 * without a pause (Gareth, 2026-11-15). One per frame on the main thread: tile drawing shares the
 * board's state.
 */
object SettingsPreviews {
    /** Height of every Settings sample strip and swatch. */
    val HEIGHT = 76.dp

    private var made = false

    /** Once only; swatches are [width] x [height] px. */
    suspend fun make(width: Int, height: Int) {
        if (made) return
        made = true
        delay(START_DELAY_MS)
        for (set in TileSet.entries) {
            SampleStrip.of(TileKits.of(set), SampleStrip.SETTINGS_TILES)
            withFrameNanos { }
        }
        for (surface in TableSurface.entries) {
            TableSurfaceRenderer.prerenderSwatch(surface, width, height)
            withFrameNanos { }
        }
    }

    // Lets the title screen settle first
    private const val START_DELAY_MS = 1500L
}
