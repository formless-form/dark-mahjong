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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The first-game performance check found this phone stuttering: the player is asked whether to
 * turn on Low-performance mode. Nothing changes unless they say yes; Back is a no. A stray tap
 * beside the card doesn't close it, so the question is never lost unanswered; nor do its buttons
 * take a tap in its first half-second, one meant for what was there before it. On a short screen
 * (on its side, or with large text) it scrolls.
 */
@Composable
fun PerformanceNotice(
    onTurnOn: () -> Unit,
    onNoThanks: () -> Unit,
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ARMING.milliseconds)
        armed = true
    }
    Dialog(onDismissRequest = onNoThanks, properties = DialogProperties(dismissOnClickOutside = false)) {
        BlurBehindDialog()
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH).fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
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
                    BigButton("Yes, turn it on", onClick = { if (armed) onTurnOn() }, primary = true, icon = Icons.Rounded.Speed, modifier = Modifier.fillMaxWidth())
                    BigButton("No thanks", onClick = { if (armed) onNoThanks() }, icon = Icons.Rounded.LocalFireDepartment, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// How long the buttons wait before taking a tap, in milliseconds
private const val ARMING = 500

const val PERFORMANCE_QUESTION =
    "The game doesn't seem to be running smoothly on your phone. Would you like to turn on " +
        "low-performance mode? It turns off the firelight and fireworks, and can be turned off in " +
        "Settings if you change your mind."
