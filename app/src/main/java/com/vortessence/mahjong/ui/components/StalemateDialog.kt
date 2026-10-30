package com.vortessence.mahjong.ui.components

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
fun StalemateDialog(
    canUndo: Boolean,
    onReshuffle: () -> Unit,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    // Back closes it, to look over the board; the Hint button brings it back
    onDismiss: () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = false)) {
        BlurBehindDialog()
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No moves left",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "None of the free tiles match. Reshuffle the tiles that are left, undo your last move, or start the board again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BigButton("Reshuffle", onClick = onReshuffle, primary = true, icon = Icons.Rounded.Shuffle, modifier = Modifier.fillMaxWidth())
                    if (canUndo) {
                        BigButton("Undo last move", onClick = onUndo, icon = Icons.AutoMirrored.Rounded.Undo, modifier = Modifier.fillMaxWidth())
                    }
                    BigButton("Restart", onClick = onRestart, icon = Icons.Rounded.RestartAlt, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Restart asks first: the same deal again from the start, or new tiles on the same layout. Back
 * or a tap outside keeps the game as it is.
 */
@Composable
fun RestartDialog(
    onSameDeal: () -> Unit,
    onNewDeal: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        BlurBehindDialog()
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Start again",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Play these same tiles again from the start, or deal new ones on this layout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BigButton("Same deal", onClick = onSameDeal, primary = true, icon = Icons.Rounded.Replay, modifier = Modifier.fillMaxWidth())
                    BigButton("New deal", onClick = onNewDeal, icon = Icons.Rounded.Shuffle, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Blurs whatever is behind the dialog's window, as Android 17 does behind its own dialogs and
 * menus, with a lighter dim since the blur already sets the dialog apart. Phones that have
 * blur turned off (battery saver, or the accessibility setting) just get the dim.
 */
@Composable
fun BlurBehindDialog(radius: Dp = 28.dp) {
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    DisposableEffect(view, radiusPx) {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radiusPx }
            window.setDimAmount(0.35f)
        }
        onDispose {}
    }
}
