package com.vortessence.mahjong.ui.components

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.TileSet
import kotlinx.coroutines.delay

/**
 * Settings' pictures, made ahead of time a little after the app opens, so Settings opens without
 * a pause (Gareth, 2026-11-15): every tile set's sample tiles ([SampleStrip]) and every table's
 * swatch ([TableSurfaceRenderer.prerenderSwatch]). One at a time, a frame apart, on the main
 * thread: the tiles' drawing shares the board's state, so it never runs alongside it.
 */
object SettingsPreviews {
    /** The height of every sample strip and swatch in Settings. */
    val HEIGHT = 76.dp

    private var made = false

    /** Makes them all, once, swatches [width] x [height] pixels (the screen's width, [HEIGHT]). */
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

    // Long enough for the title screen to settle first
    private const val START_DELAY_MS = 1500L
}
