package com.vortessence.mahjong.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.Difficulty
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.ui.components.BadgeState
import com.vortessence.mahjong.ui.components.DifficultyStars
import com.vortessence.mahjong.ui.components.ExpressiveScreen
import com.vortessence.mahjong.ui.components.LevelBadge
import com.vortessence.mahjong.ui.components.SegmentGap
import com.vortessence.mahjong.ui.components.segmentColors

@Composable
fun ConstellationSelectScreen(
    progress: Progress,
    onConstellationSelected: (Constellation) -> Unit,
    onBack: () -> Unit,
) {
    var selectedDifficultyFilter by remember { mutableStateOf<Difficulty?>(null) }
    var selectedLayoutFilter by remember { mutableStateOf<Boolean?>(null) }

    val filteredConstellations = remember(selectedDifficultyFilter, selectedLayoutFilter) {
        ConstellationRepository.constellations.filter { constellation ->
            val matchDifficulty = (selectedDifficultyFilter == null) || (constellation.difficulty == selectedDifficultyFilter)
            val isTutorial = constellation.id == ConstellationRepository.TUTORIAL_ID
            val matchLayout = isTutorial || (selectedLayoutFilter == null) || (constellation.isPortraitOptimized == selectedLayoutFilter)
            matchDifficulty && matchLayout
        }
    }

    ExpressiveScreen(title = "Layouts", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            ChipRow {
                listOf<Difficulty?>(null).plus(Difficulty.entries).forEach { diff ->
                    item {
                        FilterOption(
                            label = diff?.displayName ?: "All difficulties",
                            selected = selectedDifficultyFilter == diff,
                            onClick = { selectedDifficultyFilter = if (diff != null && selectedDifficultyFilter == diff) null else diff },
                        )
                    }
                }
            }
            ChipRow {
                listOf<Pair<String, Boolean?>>("All shapes" to null, "Tall" to true, "Wide" to false).forEach { (label, tall) ->
                    item {
                        FilterOption(
                            label = label,
                            selected = selectedLayoutFilter == tall,
                            onClick = { selectedLayoutFilter = if (tall != null && selectedLayoutFilter == tall) null else tall },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = padding.calculateBottomPadding() + 32.dp),
                verticalArrangement = Arrangement.spacedBy(SegmentGap),
            ) {
                itemsIndexed(filteredConstellations, key = { _, c -> c.id }) { index, constellation ->
                    val cleared = progress.completedConstellations.contains(constellation.id)
                    SegmentedListItem(
                        onClick = { onConstellationSelected(constellation) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = filteredConstellations.size),
                        colors = segmentColors(),
                        leadingContent = {
                            LevelBadge(
                                state = if (cleared) BadgeState.CLEARED else BadgeState.OPEN,
                                label = constellation.name.first().toString(),
                            )
                        },
                        overlineContent = { Text(layoutLabel(constellation), color = MaterialTheme.colorScheme.primary) },
                        supportingContent = {
                            Text(
                                listOf("${constellation.difficulty.displayName} · ${constellation.tileCount} tiles", constellation.description)
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n"),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = { DifficultyStars(constellation.difficulty.stars) },
                    ) {
                        Text(constellation.name)
                    }
                }
            }
        }
    }
}

// "Tall", "Wide", or for an imported layout its shape and group, e.g. "Tall · Animals"
private fun layoutLabel(constellation: Constellation): String = when {
    constellation.id == ConstellationRepository.TUTORIAL_ID -> "Guided lesson"
    else -> listOfNotNull(if (constellation.isPortraitOptimized) "Tall" else "Wide", constellation.category).joinToString(" · ")
}

@Composable
private fun ChipRow(content: LazyListScope.() -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

// A filter chip that shows a tick when selected, as Android's own filter chips do
@Composable
private fun FilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
