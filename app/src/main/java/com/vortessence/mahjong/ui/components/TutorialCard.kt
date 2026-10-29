package com.vortessence.mahjong.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.engine.TutorialAction
import com.vortessence.mahjong.engine.TutorialScript
import com.vortessence.mahjong.engine.TutorialStep

/**
 * Instruction panel shown below the board while the guided tutorial runs.
 */
@Composable
fun TutorialCard(
    step: TutorialStep,
    settings: GameSettings,
    onContinue: () -> Unit,
    onSettingsChanged: (GameSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        AnimatedContent(targetState = step, label = "TutorialStep") { current ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Step ${TutorialScript.steps.indexOf(current) + 1} of ${TutorialScript.steps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (current.showSettingsToggles) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingToggle(
                        title = "Highlight selectable tiles",
                        checked = settings.highlightFreeTiles,
                        onCheckedChange = { onSettingsChanged(settings.copy(highlightFreeTiles = it)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (current.action == TutorialAction.CONTINUE) {
                    BigButton(
                        text = current.continueLabel,
                        onClick = onContinue,
                        primary = true,
                        modifier = Modifier.align(Alignment.End)
                    )
                } else {
                    Text(
                        text = when (current.action) {
                            TutorialAction.TAP_BLOCKED -> "Tap the blue tile"
                            TutorialAction.PINCH_ZOOM -> "Pinch the board"
                            TutorialAction.ZOOM_OUT -> "Tap the magnifying glass"
                            else -> "Match the blue tiles"
                        },
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        CheckSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
