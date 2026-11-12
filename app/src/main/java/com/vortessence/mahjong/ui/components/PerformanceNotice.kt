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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The first-game performance check found this phone stuttering: the player is asked whether to
 * turn on Low-performance mode. Nothing changes unless they say yes; Back is a no. A stray tap
 * beside the card doesn't close it, so the question is never lost unanswered.
 */
@Composable
fun PerformanceNotice(
    onTurnOn: () -> Unit,
    onNoThanks: () -> Unit,
) {
    Dialog(onDismissRequest = onNoThanks, properties = DialogProperties(dismissOnClickOutside = false)) {
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
                    text = "Smoother play?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = PERFORMANCE_QUESTION,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BigButton("Yes, turn it on", onClick = onTurnOn, primary = true, icon = Icons.Rounded.Speed, modifier = Modifier.fillMaxWidth())
                    BigButton("No thanks", onClick = onNoThanks, icon = Icons.Rounded.LocalFireDepartment, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

const val PERFORMANCE_QUESTION =
    "The game doesn't seem to be running smoothly on your phone. Would you like to turn on " +
        "low-performance mode? It turns off the firelight and fireworks, and can be turned off in " +
        "Settings if you change your mind."
