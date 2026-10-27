package com.vortessence.mahjong.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Texture
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Fireplace
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.ui.components.CheckSwitch
import com.vortessence.mahjong.ui.components.ExpressiveScreen
import com.vortessence.mahjong.ui.components.LeadingIcon
import com.vortessence.mahjong.ui.components.SectionHeader
import com.vortessence.mahjong.ui.components.SegmentGap
import com.vortessence.mahjong.ui.components.segmentColors

private typealias Segment = @Composable (ListItemShapes) -> Unit

@Composable
fun SettingsScreen(
    settings: GameSettings,
    onSettingsChanged: (GameSettings) -> Unit,
    onBack: () -> Unit,
) {
    ExpressiveScreen(title = "Settings", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            SectionHeader("Sound & music")
            SegmentedGroup(
                buildList {
                    add { shapes ->
                        ToggleRow(
                            shapes, Icons.AutoMirrored.Rounded.VolumeUp, "Game sounds", "Tile taps, matches and the finish",
                            settings.soundEnabled,
                        ) { onSettingsChanged(settings.copy(soundEnabled = it)) }
                    }
                    if (settings.soundEnabled) {
                        add { shapes ->
                            SliderRow(shapes, "Sound volume", settings.soundVolume) {
                                onSettingsChanged(settings.copy(soundVolume = it))
                            }
                        }
                    }
                    add { shapes ->
                        ToggleRow(
                            shapes, Icons.Rounded.MusicNote, "Background music", "Tranquil Chinese pentatonic melody",
                            settings.musicEnabled,
                        ) { onSettingsChanged(settings.copy(musicEnabled = it)) }
                    }
                    if (settings.musicEnabled) {
                        add { shapes ->
                            SliderRow(shapes, "Music volume", settings.musicVolume) {
                                onSettingsChanged(settings.copy(musicVolume = it))
                            }
                        }
                    }
                },
            )

            SectionHeader("Gameplay")
            SegmentedGroup(
                listOf(
                    { shapes ->
                        ToggleRow(
                            shapes, Icons.Rounded.Highlight, "Highlight selectable tiles", "Dim the tiles that can't be picked yet",
                            settings.highlightFreeTiles,
                        ) { onSettingsChanged(settings.copy(highlightFreeTiles = it)) }
                    },
                    { shapes ->
                        ToggleRow(
                            shapes, Icons.Rounded.FitScreen, "Automatic board scaling", "Grow the board to fill the screen as it clears",
                            settings.autoZoomEnabled,
                        ) { onSettingsChanged(settings.copy(autoZoomEnabled = it)) }
                    },
                    { shapes ->
                        ToggleRow(
                            shapes, Icons.Rounded.Vibration, "Vibration", "Gentle feedback on taps and matches",
                            settings.vibrationEnabled,
                        ) { onSettingsChanged(settings.copy(vibrationEnabled = it)) }
                    },
                ),
            )

            SectionHeader("Appearance")
            SegmentedGroup(
                listOf<@Composable (ListItemShapes) -> Unit>(
                    { shapes ->
                        ChoiceRow(
                            shapes, Icons.Rounded.Texture, "Table surface", "What the tiles are played on",
                            TableSurface.entries, { it.displayName }, settings.tableSurface,
                        ) { onSettingsChanged(settings.copy(tableSurface = it)) }
                    },
                    { shapes ->
                        SliderRow(shapes, "Lamp brightness", settings.lampBrightness) {
                            onSettingsChanged(settings.copy(lampBrightness = it))
                        }
                    },
                    { shapes ->
                        ToggleRow(
                            shapes, Icons.Rounded.Fireplace, "Fireplace", "Firelight from a hearth beside the table",
                            settings.fireplaceLit,
                        ) { onSettingsChanged(settings.copy(fireplaceLit = it)) }
                    },
                ) + if (settings.fireplaceLit) {
                    listOf<@Composable (ListItemShapes) -> Unit>(
                        { shapes ->
                            SliderRow(shapes, "Fire intensity", settings.fireIntensity) {
                                onSettingsChanged(settings.copy(fireIntensity = it))
                            }
                        },
                        { shapes ->
                            SliderRow(shapes, "Fire sound", settings.fireVolume) {
                                onSettingsChanged(settings.copy(fireVolume = it))
                            }
                        },
                        { shapes ->
                            ToggleRow(
                                shapes, Icons.Rounded.Contrast, "Long fire shadows",
                                "Traced on the GPU every frame, so they use more battery",
                                settings.fireShadows,
                            ) { onSettingsChanged(settings.copy(fireShadows = it)) }
                        },
                    )
                } else {
                    emptyList()
                } + listOf<@Composable (ListItemShapes) -> Unit>(
                    { shapes ->
                        ToggleRow(
                            shapes, Icons.Rounded.Tag, "Corner index", "Numbers and letters in each tile's corner",
                            settings.showCornerIndex,
                        ) { onSettingsChanged(settings.copy(showCornerIndex = it)) }
                    },
                ),
            )

            SectionHeader("About")
            val uriHandler = LocalUriHandler.current
            SegmentedGroup(
                listOf(
                    { shapes ->
                        SegmentedListItem(
                            onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
                            shapes = shapes,
                            colors = segmentColors(),
                            leadingContent = { LeadingIcon(Icons.Rounded.PrivacyTip) },
                            supportingContent = { Text("Dark Mahjong collects no data at all") },
                        ) {
                            Text("Privacy policy")
                        }
                    },
                ),
            )
        }
    }
}

// Opened in the browser; the app itself has no internet access
private const val PRIVACY_POLICY_URL = "https://github.com/formless-form/dark-mahjong/blob/dark-mahjong/docs/PRIVACY.md"

// A run of list items drawn as one rounded block: big outer corners, small inner ones, hairline gaps
@Composable
private fun SegmentedGroup(segments: List<Segment>) {
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        segments.forEachIndexed { index, segment ->
            segment(ListItemDefaults.segmentedShapes(index = index, count = segments.size))
        }
    }
}

@Composable
private fun ToggleRow(
    shapes: ListItemShapes,
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        // Keep the segment's corners when switched on
        shapes = shapes.copy(selectedShape = shapes.shape),
        colors = segmentColors(),
        leadingContent = { LeadingIcon(icon) },
        trailingContent = { CheckSwitch(checked = checked, onCheckedChange = null) },
        supportingContent = { Text(summary) },
    ) {
        Text(title)
    }
}

@Composable
private fun SliderRow(shapes: ListItemShapes, title: String, value: Float, onValueChange: (Float) -> Unit) {
    SegmentedListItem(
        shapes = shapes,
        colors = segmentColors(),
        // Lines the slider up with the titles above, past the leading icons
        leadingContent = { Spacer(Modifier.width(40.dp)) },
        trailingContent = {
            Text(
                "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                colors = SliderDefaults.colors(
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) {
        Text(title)
    }
}

// A choice of a few: a connected button group, the chosen one filled in and fully rounded
@Composable
private fun <T> ChoiceRow(
    shapes: ListItemShapes,
    icon: ImageVector,
    title: String,
    subtitle: String,
    choices: List<T>,
    label: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Surface(shape = shapes.shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LeadingIcon(icon)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.fillMaxWidth(),
            ) {
                choices.forEachIndexed { index, choice ->
                    ToggleButton(
                        checked = choice == selected,
                        onCheckedChange = { onSelect(choice) },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton },
                    ) {
                        Text(label(choice), maxLines = 1)
                    }
                }
            }
        }
    }
}
