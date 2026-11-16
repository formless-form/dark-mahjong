package com.vortessence.mahjong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shared Android 17 (M3 Expressive) building blocks for every menu screen

/** As in Android 17's own Settings. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
    }
}

/** A menu screen whose large title shrinks into the bar on scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveScreen(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    // Behind the whole screen, bar included, instead of the page colour
    backdrop: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val page = if (backdrop != null) Color.Transparent else MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
    backdrop?.invoke()
    CompositionLocalProvider(LocalPanelAlpha provides if (backdrop != null) PANEL_ALPHA_OVER_BACKDROP else 1f) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = { BackButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = page,
                    // Slightly see-through over a backdrop
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (backdrop != null) 0.88f else 1f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    subtitleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = page,
        content = content,
    )
    }
    }
}

/** Menu panel opacity: 1 on the plain page, faint over the stone backdrop so it shows through. */
val LocalPanelAlpha = staticCompositionLocalOf { 1f }

// Enough to group rows, little enough for the stone to read through
private const val PANEL_ALPHA_OVER_BACKDROP = 0.34f

/** [colour] at this screen's panel alpha, but at least [atLeast] of its own (legible controls). */
@Composable
fun panel(colour: Color, atLeast: Float = 0f): Color {
    val alpha = LocalPanelAlpha.current
    if (alpha >= 1f) return colour
    // Half-desaturated over the stone, to read as veils of it
    val grey = 0.2126f * colour.red + 0.7152f * colour.green + 0.0722f * colour.blue
    return Color(
        red = grey + (colour.red - grey) * PANEL_SATURATION_OVER_BACKDROP,
        green = grey + (colour.green - grey) * PANEL_SATURATION_OVER_BACKDROP,
        blue = grey + (colour.blue - grey) * PANEL_SATURATION_OVER_BACKDROP,
        alpha = colour.alpha * maxOf(alpha, atLeast),
    )
}

private const val PANEL_SATURATION_OVER_BACKDROP = 0.5f

/** The accent heading above a group of list items. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        // Small title + 6 sp (chosen 2026-10-30)
        style = MaterialTheme.typography.titleSmallEmphasized.let { it.copy(fontSize = (it.fontSize.value + 6f).sp) },
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

val SegmentGap: Dp = ListItemDefaults.SegmentedGap

/**
 * Slightly raised from the page, so each group reads as one block. On rows look like off ones (the
 * switch shows state), as in Android's Settings.
 */
@Composable
fun segmentColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = panel(MaterialTheme.colorScheme.surfaceContainer),
    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledContainerColor = panel(MaterialTheme.colorScheme.surfaceContainerLow),
    selectedContainerColor = panel(MaterialTheme.colorScheme.surfaceContainer),
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedLeadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedOverlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** A list item's leading icon in a soft circle. */
@Composable
fun LeadingIcon(icon: ImageVector, enabled: Boolean = true, color: Color? = null) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(panel(MaterialTheme.colorScheme.surfaceContainerHighest, atLeast = 0.35f), CircleShape),
    ) {
        // 38%, as Material greys disabled text
        val tint = (color ?: MaterialTheme.colorScheme.primary).let { if (enabled) it else it.copy(alpha = 0.38f) }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Thumb shows a tick when on. */
@Composable
fun CheckSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = if (checked) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
    )
}

val BigButtonHeight: Dp = ButtonDefaults.MediumContainerHeight

/** A pill button whose corners square off slightly when pressed. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    // Narrower padding, for buttons sharing a row on small phones
    compact: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(BigButtonHeight)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(BigButtonHeight)))
        }
        ProvideTextStyle(ButtonDefaults.textStyleFor(BigButtonHeight)) { Text(text, maxLines = 1, softWrap = false) }
    }
    val sized = modifier.heightIn(min = BigButtonHeight)
    val padding = if (compact) {
        PaddingValues(horizontal = 12.dp)
    } else {
        ButtonDefaults.contentPaddingFor(BigButtonHeight, hasStartIcon = icon != null)
    }
    if (primary) {
        Button(onClick = onClick, shapes = ButtonDefaults.shapesFor(BigButtonHeight), modifier = sized, contentPadding = padding, content = content)
    } else {
        FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapesFor(BigButtonHeight),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = panel(MaterialTheme.colorScheme.surfaceContainerHighest, atLeast = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = sized,
            contentPadding = padding,
            content = content,
        )
    }
}

/** Material's shape-morphing indicator, centred. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingPage(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
