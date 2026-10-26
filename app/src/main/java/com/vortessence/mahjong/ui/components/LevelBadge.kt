package com.vortessence.mahjong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class BadgeState { CURRENT, CLEARED, OPEN, LOCKED }

/**
 * The leading badge of a level or layout row, using Material 3 Expressive's shape set: a sunny
 * burst for the level you're up to, a soft cookie with a tick once cleared, a plain circle
 * otherwise.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LevelBadge(state: BadgeState, label: String, icon: ImageVector? = null) {
    val colors = MaterialTheme.colorScheme
    val (shape, container) = when (state) {
        BadgeState.CURRENT -> MaterialShapes.Sunny.toShape() to colors.primary
        BadgeState.CLEARED -> MaterialShapes.Cookie9Sided.toShape() to colors.primaryContainer
        BadgeState.OPEN -> CircleShape to colors.surfaceContainerHighest
        BadgeState.LOCKED -> CircleShape to colors.surfaceContainerHigh
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .background(container, shape),
    ) {
        when (state) {
            BadgeState.CLEARED -> Icon(Icons.Rounded.Check, contentDescription = "Cleared", tint = colors.onPrimaryContainer)
            BadgeState.LOCKED -> Icon(
                Icons.Rounded.Lock,
                contentDescription = "Locked",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp).alpha(0.6f),
            )
            else -> if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (state == BadgeState.CURRENT) colors.onPrimary else colors.primary)
            } else Text(
                label,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = if (state == BadgeState.CURRENT) colors.onPrimary else colors.primary,
            )
        }
    }
}

/** Difficulty as a short row of small amber stars. */
@Composable
fun DifficultyStars(stars: Int, dimmed: Boolean = false) {
    Row(Modifier.alpha(if (dimmed) 0.4f else 1f)) {
        repeat(stars) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
        }
    }
}
