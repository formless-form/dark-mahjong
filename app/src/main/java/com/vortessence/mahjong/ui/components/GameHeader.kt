package com.vortessence.mahjong.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.repository.levelLabel

/** Above the board: a round back button, then which level and layout this is. */
@Composable
fun GameHeader(
    gameState: GameState,
    levelIndex: Int?,
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
                    levelLabel(levelIndex)?.takeIf { it != gameState.constellation.name },
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

/** "Level 8", or "Tutorial" for the tutorial (level 0); null outside the journey. */
/**
 * Below the board: Material 3 Expressive's floating toolbar holding big labelled buttons, within
 * easy reach of the thumb.
 */
@Composable
fun GameToolbar(
    gameState: GameState,
    onHintClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRestartClick: () -> Unit,
    modifier: Modifier = Modifier,
    hintAndUndoEnabled: Boolean = true,
) {
    val canHint = hintAndUndoEnabled && gameState.availablePairsCount > 0
    val canUndo = hintAndUndoEnabled && gameState.moveHistory.isNotEmpty()

    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                toolbarContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolbarAction(Icons.Rounded.Lightbulb, "Hint", enabled = canHint, onClick = onHintClick, tint = MaterialTheme.colorScheme.secondary)
                ToolbarAction(Icons.AutoMirrored.Rounded.Undo, "Undo", enabled = canUndo, onClick = onUndoClick)
                ToolbarAction(Icons.Rounded.RestartAlt, "Restart", enabled = true, onClick = onRestartClick)
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
) {
    val content = MaterialTheme.colorScheme.onSurface
    val alpha = if (enabled) 1f else 0.38f
    // 20 px shorter than Material's 60 dp, 10 px off the top and bottom, so the bar sits slimmer;
    // the bar's rounded ends follow its height
    val height = 60.dp - with(LocalDensity.current) { 20.toDp() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        modifier = Modifier.widthIn(min = 88.dp).height(height),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = (if (tint == Color.Unspecified) content else tint).copy(alpha = alpha),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = alpha))
        }
    }
}
