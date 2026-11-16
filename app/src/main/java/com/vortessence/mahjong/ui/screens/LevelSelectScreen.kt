package com.vortessence.mahjong.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import com.vortessence.mahjong.data.model.LevelRecord
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.ui.components.BadgeState
import com.vortessence.mahjong.ui.components.DifficultyStars
import com.vortessence.mahjong.ui.components.ExpressiveScreen
import com.vortessence.mahjong.ui.components.MenuBackdrop
import com.vortessence.mahjong.ui.components.LevelBadge
import com.vortessence.mahjong.ui.components.SegmentGap
import com.vortessence.mahjong.ui.components.segmentColors

@Composable
fun LevelSelectScreen(
    progress: Progress,
    levels: List<Constellation> = ConstellationRepository.levelConstellations,
    tutorial: Constellation = ConstellationRepository.tutorialConstellation,
    onLevelSelected: (Int) -> Unit,
    onBack: () -> Unit,
    fireIntensity: Float = 0.5f,
    // Off with the phone's Remove animations
    fire: Boolean = true,
    // For previews; defaults to near the level reached
    openAt: Int? = null,
) {
    val highestUnlocked = progress.highestLevelReached.coerceAtLeast(1)
    // Tutorial first (always open)
    val rows = listOf(ConstellationRepository.TUTORIAL_LEVEL to tutorial) + levels.mapIndexed { i, c -> (i + 1) to c }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (openAt ?: (highestUnlocked - 1)).coerceIn(0, rows.lastIndex))

    ExpressiveScreen(title = "Journey", onBack = onBack, backdrop = { MenuBackdrop(fireIntensity, fire) }) { padding ->
        // So the list never shows through the title bar
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(SegmentGap),
        ) {
            itemsIndexed(rows, key = { _, row -> row.first }) { index, (levelIndex, constellation) ->
                val isTutorial = levelIndex == ConstellationRepository.TUTORIAL_LEVEL
                val unlocked = isTutorial || levelIndex <= highestUnlocked
                val cleared = progress.completedConstellations.contains(constellation.id)
                // Locked and current first: a layout cleared under Layouts may be an unreached or current level
                val state = when {
                    !unlocked -> BadgeState.LOCKED
                    levelIndex == highestUnlocked -> BadgeState.CURRENT
                    cleared -> BadgeState.CLEARED
                    else -> BadgeState.OPEN
                }
                SegmentedListItem(
                    onClick = { onLevelSelected(levelIndex) },
                    enabled = unlocked,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size),
                    colors = segmentColors(),
                    leadingContent = {
                        LevelBadge(
                            state,
                            label = levelIndex.toString(),
                            icon = if (isTutorial) Icons.Rounded.School else null,
                            ribbon = constellation.id in progress.cleanConstellations,
                        )
                    },
                    overlineContent = {
                        Text(
                            if (isTutorial) "Tutorial" else "Level $levelIndex",
                            color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    // Best clear beneath (Gareth's pick of four designs, 2026-11-13)
                    supportingContent = {
                        val line = "${constellation.difficulty.displayName} · ${constellation.tileCount} tiles"
                        val record = progress.records[constellation.id]
                        if (record == null) {
                            Text(line)
                        } else {
                            Column {
                                Text(line)
                                Spacer(Modifier.height(6.dp))
                                RecordChips(record)
                            }
                        }
                    },
                    trailingContent = { DifficultyStars(constellation.difficulty.stars, dimmed = !unlocked) },
                ) {
                    Text(constellation.name)
                }
            }
        }
    }
}

// Time, then hints, reshuffles and undos (dimmed when none)
@Composable
private fun RecordChips(record: LevelRecord) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(Icons.Rounded.Timer, record.time, true)
        Chip(Icons.Rounded.Lightbulb, "${record.hints}", record.hints > 0)
        Chip(Icons.Rounded.Shuffle, "${record.reshuffles}", record.reshuffles > 0)
        Chip(Icons.AutoMirrored.Rounded.Undo, "${record.undos}", record.undos > 0)
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String, strong: Boolean) {
    val colour = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = colour)
    }
}
