package com.vortessence.mahjong.ui.components

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vortessence.mahjong.data.model.GameState

/** Above the board: back button, level and layout. */
@Composable
fun GameHeader(
    gameState: GameState,
    // "Level 12", "Today's Puzzle", or null for a layout chosen from Layouts
    label: String?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 5.5.dp),
    ) {
        BackButton(onClick = onBackClick)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(
                    label?.takeIf { it != gameState.constellation.name },
                    gameState.constellation.difficulty.displayName,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = gameState.constellation.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Below the board, in thumb reach: M3 Expressive floating toolbar of labelled buttons. */
@Composable
fun GameToolbar(
    gameState: GameState,
    onHintClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRestartClick: () -> Unit,
    modifier: Modifier = Modifier,
    hintAndUndoEnabled: Boolean = true,
    // Shown on the button; null where free (the tutorial)
    hints: Int? = null,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                toolbarContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolbarActions(gameState, onHintClick, onUndoClick, onRestartClick, hintAndUndoEnabled, hints = hints)
            }
        }
    }
}

/** On-side boards (height is short): header and toolbar as one narrow rail down the left. */
@Composable
fun GameRail(
    gameState: GameState,
    // "Level 12", "Today's Puzzle", or null for a layout chosen from Layouts
    label: String?,
    onBackClick: () -> Unit,
    onHintClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRestartClick: () -> Unit,
    modifier: Modifier = Modifier,
    hintAndUndoEnabled: Boolean = true,
    hints: Int? = null,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(RAIL_WIDTH)
            .padding(start = RAIL_START, end = 4.dp, top = 12.dp, bottom = 12.dp),
    ) {
        BackButton(onClick = onBackClick)
        Spacer(Modifier.height(8.dp))
        // "Level 12", "Hard" on separate lines
        Text(
            text = listOfNotNull(
                label?.takeIf { it != gameState.constellation.name },
                gameState.constellation.difficulty.displayName,
            ).joinToString("\n"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        // A word per line
        var nameWidth by remember { mutableIntStateOf(0) }
        Text(
            text = gameState.constellation.name.split(' ').joinToString("\n"),
            // Tight, to read as one name
            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 19.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.onSizeChanged { nameWidth = it.width },
        )
        Spacer(Modifier.weight(1f))
        // Toolbar buttons stacked on the rail, as wide as the bottom toolbar is tall, centred under
        // the name but never past the screen edge
        val toolbarWidth = actionHeight() + TOOLBAR_PADDING * 2
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .offset {
                    IntOffset(((nameWidth - toolbarWidth.roundToPx()) / 2).coerceAtLeast(-RAIL_START.roundToPx()), 0)
                }
                .width(toolbarWidth)
                .padding(TOOLBAR_PADDING),
        ) {
            ToolbarActions(gameState, onHintClick, onUndoClick, onRestartClick, hintAndUndoEnabled, narrow = true, hints = hints)
        }
        Spacer(Modifier.height(16.dp))
    }
}

private val RAIL_WIDTH = 100.dp
private val RAIL_START = 12.dp

// As Material's floating toolbar pads its buttons
private val TOOLBAR_PADDING = 8.dp

@Composable
private fun ToolbarActions(
    gameState: GameState,
    onHintClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRestartClick: () -> Unit,
    hintAndUndoEnabled: Boolean,
    narrow: Boolean = false,
    hints: Int? = null,
) {
    // When stuck, Hint reopens the "no moves" dialog
    val canHint = hintAndUndoEnabled && (gameState.availablePairsCount > 0 || gameState.isStalemate)
    val canUndo = hintAndUndoEnabled && gameState.moveHistory.isNotEmpty()
    // Greyed with none left, but a tap explains how to earn one
    ToolbarAction(
        Icons.Rounded.Lightbulb, "Hint", enabled = canHint, onClick = onHintClick, narrow = narrow,
        tint = MaterialTheme.colorScheme.secondary, dimmed = hints == 0, count = hints,
    )
    ToolbarAction(Icons.AutoMirrored.Rounded.Undo, "Undo", enabled = canUndo, onClick = onUndoClick, narrow = narrow)
    ToolbarAction(Icons.Rounded.RestartAlt, "Restart", enabled = true, onClick = onRestartClick, narrow = narrow)
}

// 20 px under Material's 60 dp, for a slimmer bar
@Composable
private fun actionHeight() = 60.dp - with(LocalDensity.current) { 20.toDp() }

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    // The rail's: full toolbar width, label close to the edges
    narrow: Boolean = false,
    tint: Color = Color.Unspecified,
    // Greyed but still tappable
    dimmed: Boolean = false,
    // Badge on the icon's corner, Android style
    count: Int? = null,
) {
    val content = MaterialTheme.colorScheme.onSurface
    val alpha = if (enabled && !dimmed) 1f else 0.38f
    Surface(
        onClick = onClick,
        enabled = enabled,
        // Narrow: tighter corners, so they don't clip "Restart"
        shape = if (narrow) RoundedCornerShape(12.dp) else MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        modifier = (if (narrow) Modifier.fillMaxWidth() else Modifier.widthIn(min = 88.dp)).height(actionHeight()),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = if (narrow) 0.dp else 12.dp),
        ) {
            Box {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = (if (tint == Color.Unspecified) content else tint).copy(alpha = alpha),
                    modifier = Modifier.size(if (narrow) 22.dp else 24.dp),
                )
                if (count != null) {
                    CountBadge(count, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = 0.dp).alpha(if (enabled) 1f else 0.38f))
                }
            }
            Spacer(Modifier.height(if (narrow) 3.dp else 4.dp))
            // Narrow: a size smaller, so "Restart" clears the rounded ends
            Text(
                label,
                style = if (narrow) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = content.copy(alpha = alpha),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// Hints left in a jade disc (grey at none)
@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(12.dp)
            .background(if (count > 0) colors.primary else colors.surfaceContainerHighest, CircleShape),
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, lineHeight = 8.5.sp),
            color = if (count > 0) colors.onPrimary else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
