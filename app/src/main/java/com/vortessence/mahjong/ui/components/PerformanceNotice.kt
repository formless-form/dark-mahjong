package com.vortessence.mahjong.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * The first-game performance check found this phone dropping frames: Low-performance mode is
 * switched on as the card appears ([onShown]), and the player can keep it or turn the effects back
 * on. Closed any other way, the mode stays.
 */
@Composable
fun PerformanceNotice(
    fps: Int,
    onShown: () -> Unit,
    onKeep: () -> Unit,
    onTurnBackOn: () -> Unit,
) {
    LaunchedEffect(Unit) { onShown() }
    Dialog(onDismissRequest = onKeep) {
        BlurBehindDialog()
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH).fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Smoother play on this phone",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = performanceNoticeText(fps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BigButton("Keep it", onClick = onKeep, primary = true, icon = Icons.Rounded.Speed, modifier = Modifier.fillMaxWidth())
                    BigButton("Turn effects back on", onClick = onTurnBackOn, icon = Icons.Rounded.LocalFireDepartment, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The card's explanation, with the frame rate the check found. */
fun performanceNoticeText(fps: Int): String {
    val rate = if (fps in 1..59) " (about $fps frames a second, where 60 is smooth)" else ""
    return "Your first game was dropping frames$rate. Low-performance mode is now on: the firelight " +
        "and fireworks are off, so play stays smooth. You can change this anytime in Settings."
}
