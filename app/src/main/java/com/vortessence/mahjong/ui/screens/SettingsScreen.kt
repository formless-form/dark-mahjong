package com.vortessence.mahjong.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PrivacyTip
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
import com.vortessence.mahjong.ui.components.panel
import com.vortessence.mahjong.ui.components.MenuBackdrop
import com.vortessence.mahjong.ui.components.segmentColors

private typealias Segment = @Composable (ListItemShapes) -> Unit

@Composable
fun SettingsScreen(
    settings: GameSettings,
    onSettingsChanged: (GameSettings) -> Unit,
    onBack: () -> Unit,
    // A slider moving shows its value without saving it; it's saved when let go
    onSettingsPreview: (GameSettings) -> Unit = onSettingsChanged,
    onSettingsCommit: () -> Unit = {},
) {
    ExpressiveScreen(
        title = "Settings",
        onBack = onBack,
        backdrop = { MenuBackdrop(settings.fireIntensity) },
    ) { padding ->
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
                            shapes, Icons.AutoMirrored.Rounded.VolumeUp, "Game sounds", null,
                            settings.soundEnabled,
                        ) { onSettingsChanged(settings.copy(soundEnabled = it)) }
                    }
                    if (settings.soundEnabled) {
                        add { shapes ->
                            SliderRow(shapes, "Sound volume", settings.soundVolume, onSettingsCommit) {
                                onSettingsPreview(settings.copy(soundVolume = it))
                            }
                        }
                    }
                    add { shapes ->
                        ToggleRow(
                            shapes, Icons.Rounded.MusicNote, "Background music", null,
                            settings.musicEnabled,
                        ) { onSettingsChanged(settings.copy(musicEnabled = it)) }
                    }
                    if (settings.musicEnabled) {
                        add { shapes ->
                            SliderRow(shapes, "Music volume", settings.musicVolume, onSettingsCommit) {
                                onSettingsPreview(settings.copy(musicVolume = it))
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
                            shapes, Icons.Rounded.Texture, "Table surface", null,
                            TableSurface.entries, { it.shortName }, settings.tableSurface,
                        ) { onSettingsChanged(settings.copy(tableSurface = it)) }
                    },
                    { shapes ->
                        SliderRow(shapes, "Lamp brightness", settings.lampBrightness, onSettingsCommit) {
                            onSettingsPreview(settings.copy(lampBrightness = it))
                        }
                    },
                    { shapes ->
                        ToggleRow(
                            shapes, Icons.Rounded.Fireplace, "Fireplace", "Firelight from a hearth beside the table. More GPU-intensive",
                            settings.fireplaceLit,
                        ) { onSettingsChanged(settings.copy(fireplaceLit = it)) }
                    },
                ) + if (settings.fireplaceLit) {
                    listOf<@Composable (ListItemShapes) -> Unit>(
                        { shapes ->
                            SliderRow(shapes, "Fire intensity", settings.fireIntensity, onSettingsCommit) {
                                onSettingsPreview(settings.copy(fireIntensity = it))
                            }
                        },
                        { shapes ->
                            SliderRow(shapes, "Fire sound", settings.fireVolume, onSettingsCommit) {
                                onSettingsPreview(settings.copy(fireVolume = it))
                            }
                        },
                    )
                } else {
                    emptyList()
                },
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
            // The game's own source: a tile of its own, just below About's
            Spacer(Modifier.height(12.dp))
            SegmentedGroup(
                listOf(
                    { shapes ->
                        SegmentedListItem(
                            onClick = { uriHandler.openUri(SOURCE_CODE_URL) },
                            shapes = shapes,
                            colors = segmentColors(),
                            leadingContent = { LeadingIcon(Icons.Rounded.Code) },
                            supportingContent = { Text("Free and open source on GitHub · GPL v3") },
                        ) {
                            Text("Dark Mahjong source code")
                        }
                    },
                ),
            )

            // Everyone whose work the game is made from (as NOTICE.md), starting with the game it
            // grew from: quiet, small rows in one panel, no icons. Each opens its source in the
            // browser
            SectionHeader("Credits")
            Surface(
                shape = MaterialTheme.shapes.large,
                color = panel(MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    CREDITS.forEachIndexed { index, (group, credits) ->
                        Text(
                            group.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = if (index == 0) 8.dp else 16.dp, bottom = 2.dp),
                        )
                        credits.forEach { credit ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { uriHandler.openUri(credit.url) }
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Text(credit.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(credit.by, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private class Credit(val title: String, val by: String, val url: String)

// The credits in groups, each under a small label, the game it grew from first
private val CREDITS = listOf(
    "Code" to listOf(
        Credit("Mahjong Solitaire", "The game Dark Mahjong grew from, by PcmciaKai · GPL v3", "https://github.com/PcmciaKai/mahjong_solitaire"),
    ),
    "Art & layouts" to listOf(
        Credit("Tile artwork", "samoheen · CC0", "https://github.com/samoheen/mahjong-tiles"),
        Credit("Layouts from Mah", "ffalt · MIT", "https://github.com/ffalt/mah"),
        Credit("Title-screen photograph", "Huangdan2060, Wikimedia Commons · CC0", "https://commons.wikimedia.org/wiki/File:Sanqing_Mountain_is_surrounded_by_clouds_and_mists1.jpg"),
        Credit("Thai straw mat pattern", "Modelled on a photograph by Willuconquer · CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Cambodian_mat_motifs.jpg"),
    ),
    "Sound & music" to listOf(
        Credit(
            "Sounds",
            "‘Tile taps’ by Kenney, ‘Tile match’ by Grevillea Labs, ‘Crackling woodfire’ by Chris C. Courter, " +
                "and ‘Win fireworks’ by greysound (both on Freesound) · CC0",
            NOTICE_URL,
        ),
        Credit("Background music", "Made with Suno · CC0", NOTICE_URL),
    ),
    "Fonts" to listOf(
        Credit(
            "Google Sans Flex, Cinzel and Seal Script",
            "The Google Sans Flex Project Authors and Natanael Gama (OFL 1.1), and CNS11643, " +
                "Ministry of Digital Affairs, Taiwan (Open Government Data Licence)",
            NOTICE_URL,
        ),
    ),
)

// The full notices and licences, for credits with no page of their own (or several)
private const val NOTICE_URL = "https://github.com/formless-form/dark-mahjong/blob/dark-mahjong/NOTICE.md"

private const val SOURCE_CODE_URL = "https://github.com/formless-form/dark-mahjong"

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
    summary: String?,
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
        supportingContent = summary?.let { { Text(it) } },
    ) {
        Text(title)
    }
}

@Composable
private fun SliderRow(
    shapes: ListItemShapes,
    title: String,
    value: Float,
    onValueChangeFinished: () -> Unit,
    onValueChange: (Float) -> Unit,
) {
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
                onValueChangeFinished = onValueChangeFinished,
                colors = SliderDefaults.colors(
                    inactiveTrackColor = panel(MaterialTheme.colorScheme.surfaceContainerHighest, atLeast = 0.5f),
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
    subtitle: String?,
    choices: List<T>,
    label: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Surface(shape = shapes.shape, color = panel(MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LeadingIcon(icon)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                            containerColor = panel(MaterialTheme.colorScheme.surfaceContainerHighest, atLeast = 0.4f),
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
