package com.vortessence.mahjong.ui.components

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.TilePosition

/**
 * Whether a layout's tiles come out [WIDE_GAIN] times larger in landscape, fitted to a typical
 * 412 x 915 dp phone less its header and toolbar (upright) or side rail (on its side).
 */
fun isWideBoard(tiles: List<BoardTile>, onSide: Boolean = false): Boolean {
    // Constellation.onSide layouts always are
    if (onSide) return true
    if (tiles.isEmpty()) return false
    val upright = computeBoardTransform(tiles, 412f, 740f, PADDING).unitX
    val onItsSide = computeBoardTransform(tiles, 753f, 388f, PADDING).unitX
    return onItsSide >= upright * WIDE_GAIN
}

// Only Nine Dragon Pagoda (x1.9) today; Classic Turtle (x1.6) and Mini Turtle (x1.5) play fine upright
const val WIDE_GAIN = 1.75f

private const val PADDING = 16f

/** For a layout's label in Layouts; none when about even. */
enum class LayoutShape(val label: String?) {
    TALL("Tall"),
    EVEN(null),
    WIDE("Wide"),
}

fun layoutShape(positions: List<TilePosition>): LayoutShape {
    val bounds = BoardBounds.of(positions)
    val aspect = bounds.width * TableCamera.nearEdgeScale() / bounds.height
    return when {
        aspect >= WIDE_ASPECT -> LayoutShape.WIDE
        aspect <= TALL_ASPECT -> LayoutShape.TALL
        else -> LayoutShape.EVEN
    }
}

// Width/height as drawn, in the clear gaps between wide (3.1 to 1.6), even (1.2 to 0.74) and tall
// (Cactus 0.61, phone levels 0.48 down)
private const val WIDE_ASPECT = 1.4f
private const val TALL_ASPECT = 0.65f

/** Landscape (either way round) while [landscape]; portrait otherwise and on leaving. */
@Composable
fun HoldOrientation(landscape: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, landscape) {
        activity.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
}
