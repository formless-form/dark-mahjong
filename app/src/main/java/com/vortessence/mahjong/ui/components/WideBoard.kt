package com.vortessence.mahjong.ui.components

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.TilePosition

/**
 * Whether a layout plays much bigger with the phone on its side: fitted to a typical phone's game
 * area (412 x 915 dp, less the header and toolbar upright, or the side rail on its side), its tiles
 * come out at least [WIDE_GAIN] times larger in landscape. Worked out from the board's shape, so a
 * new layout counts as soon as it's added.
 */
fun isWideBoard(tiles: List<BoardTile>): Boolean {
    if (tiles.isEmpty()) return false
    val upright = computeBoardTransform(tiles, 412f, 740f, PADDING).unitX
    val onItsSide = computeBoardTransform(tiles, 753f, 388f, PADDING).unitX
    return onItsSide >= upright * WIDE_GAIN
}

// Only a truly wide board gains this much: today Nine Dragon Pagoda (x1.9). Classic Turtle (x1.6) and
// Mini Turtle (x1.5) play well enough upright, and the next widest gain about x1.1
const val WIDE_GAIN = 1.75f

private const val PADDING = 16f

/** A layout's shape as it lies on the table, for its label in Layouts: none when about even. */
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

// Width over height as drawn. The layouts fall in three groups with clear gaps between: the wide
// (Nine Dragon Pagoda 3.1, Classic Turtle 1.7, Mini Turtle 1.6), the about even (1.2 to 0.74) and
// the tall (Cactus 0.61, and the tall phone levels from 0.48 down)
private const val WIDE_ASPECT = 1.4f
private const val TALL_ASPECT = 0.65f

/**
 * While [landscape], the screen is held on its side (either way round, as the phone is turned);
 * otherwise, and once it leaves, upright as the rest of the game.
 */
@Composable
fun HoldOrientation(landscape: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, landscape) {
        activity.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
}
