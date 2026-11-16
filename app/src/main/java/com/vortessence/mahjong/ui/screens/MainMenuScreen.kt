package com.vortessence.mahjong.ui.screens

import com.vortessence.mahjong.ui.components.BigButton
import com.vortessence.mahjong.data.model.Difficulty
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vortessence.mahjong.R
import com.vortessence.mahjong.data.model.ContinueInfo
import com.vortessence.mahjong.data.model.UpdateNotice
import com.vortessence.mahjong.ui.components.NameSeal
import com.vortessence.mahjong.ui.components.TitleBackdrop
import com.vortessence.mahjong.ui.theme.Cinzel
import com.vortessence.mahjong.ui.theme.JadeAccent
import com.vortessence.mahjong.ui.theme.MahjongTheme
import com.vortessence.mahjong.ui.theme.SansText
import com.vortessence.mahjong.ui.theme.SansTitle
import com.vortessence.mahjong.ui.viewmodel.TODAYS_PUZZLE

private val TitleText = Color(0xFFE6DFD1)
private val TitleTextBright = Color(0xFFFFF7E8)
private val Shade = Color(0xFF080C0B)

// Background photo is CC0: no on-screen credit needed; credited in NOTICE.md and the README

/** Seal and title in the sky at the top; text menu over the dark forest at the bottom. */
@Composable
fun MainMenuScreen(
    continueInfo: ContinueInfo? = null,
    menuStyle: TitleMenuStyle = TitleMenuStyle.SANS,
    onContinue: () -> Unit = {},
    // "Saturday · Expert"
    todaysPuzzle: String? = null,
    todaysPuzzleSolved: Boolean = false,
    onTodaysPuzzle: () -> Unit = {},
    // Null for any difficulty
    onQuickGame: (Difficulty?) -> Unit = {},
    onPlayLevels: () -> Unit,
    onChooseConstellation: () -> Unit,
    onSettings: () -> Unit,
    onMenuClick: () -> Unit = {},
    // Play builds only
    update: UpdateNotice? = null,
    onUpdate: () -> Unit = {},
) {
    var pickingQuickGame by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(Shade)) {
        if (pickingQuickGame) {
            QuickGamePicker(
                onPick = { difficulty ->
                    pickingQuickGame = false
                    onMenuClick()
                    onQuickGame(difficulty)
                },
                onDismiss = { pickingQuickGame = false },
            )
        }
        // Never stretched; film grain in the same pass
        TitleBackdrop(R.drawable.title_mountains, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Shade.copy(alpha = 0.72f),
                        0.3f to Shade.copy(alpha = 0.18f),
                        0.55f to Color.Transparent,
                        0.78f to Shade.copy(alpha = 0.25f),
                        1f to Shade.copy(alpha = 0.6f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(start = 40.dp, top = 72.dp),
        ) {
            NameSeal(size = 70.dp)
            // Sans: about 10 dp visible gap under the seal rather than 20 (the rest is the font's own)
            Spacer(Modifier.height(if (menuStyle == TitleMenuStyle.SANS) 4.dp else 14.dp))
            Text(
                text = "DARK MAHJONG",
                style = when (menuStyle) {
                    TitleMenuStyle.SERIF -> titleStyle(TitleText, size = 19.sp)
                    TitleMenuStyle.SANS -> titleStyle(TitleText, size = 22.sp).copy(fontWeight = FontWeight.Bold, letterSpacing = TITLE_SPACING.em)
                },
            )
        }

        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.displayCutout)
                // 40 px higher than the original 64 dp (2026-10-30)
                .padding(start = 40.dp, end = 40.dp, bottom = 64.dp + with(LocalDensity.current) { 40.toDp() })
                .fillMaxWidth(),
        ) {
            if (continueInfo != null) {
                MenuItem("Continue", menuStyle, subtitle = continueInfo.title, onMenuClick = onMenuClick, onClick = onContinue)
            }
            if (todaysPuzzle != null) {
                MenuItem(
                    TODAYS_PUZZLE, menuStyle, subtitle = todaysPuzzle, ticked = todaysPuzzleSolved,
                    onMenuClick = onMenuClick, onClick = onTodaysPuzzle,
                )
            }
            MenuItem("Quick Game", menuStyle, onMenuClick = onMenuClick, onClick = { pickingQuickGame = true })
            MenuItem("Journey", menuStyle, onMenuClick = onMenuClick, onClick = onPlayLevels)
            MenuItem("Layouts", menuStyle, onMenuClick = onMenuClick, onClick = onChooseConstellation)
            MenuItem("Settings", menuStyle, onMenuClick = onMenuClick, onClick = onSettings)
        }

        // System sans renders crispest at this size. Lowest letters 5 px above the edge (the line
        // box adds about 1 px)
        val bottomGap = with(LocalDensity.current) { 4.toDp() }
        val context = LocalContext.current
        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(bottom = bottomGap),
        ) {
            // Where it sat above the since-removed photo credit
            if (version != null) {
                val footStyle = TextStyle(
                    fontFamily = SansText,
                    fontSize = 14.sp,
                    letterSpacing = 0.02.em,
                    color = TitleText.copy(alpha = 0.4f),
                )
                val gap = with(LocalDensity.current) { 40.toDp() + 7.6.sp.toDp() }
                if (update != null) {
                    // Tap strip taller than the line, to be easy to hit; not while downloading
                    Text(
                        text = buildAnnotatedString {
                            append("Version $version  ·  ")
                            withStyle(SpanStyle(color = UpdateTint)) { append(updateText(update)) }
                        },
                        style = footStyle,
                        modifier = Modifier
                            .clickable(enabled = update !is UpdateNotice.Downloading, onClick = onUpdate)
                            .padding(horizontal = 12.dp, vertical = UPDATE_TAP_PAD),
                    )
                    Spacer(Modifier.height(gap - UPDATE_TAP_PAD))
                } else {
                    Text(text = "Version $version", style = footStyle)
                    Spacer(Modifier.height(gap))
                }
            }
        }
    }
}

/** SANS: Google Sans Flex under a bold Cinzel title (default since 2026-10-26); SERIF: Cinzel (the original). */
enum class TitleMenuStyle { SERIF, SANS }

// Ems; chosen 2026-10-29 (was 0.28)
private const val TITLE_SPACING = 0.12f

private fun titleStyle(color: Color, size: TextUnit = 17.sp) = TextStyle(
    fontFamily = Cinzel,
    fontSize = size,
    letterSpacing = 0.32.em,
    color = color,
)

private val SUBTITLE_LIFT = (-6).dp
private val MENU_ROW = 56.dp
private val MENU_NAME_TOP = 8.dp

// Update mention and Today's Puzzle tick
private val UpdateTint = JadeAccent

private val UPDATE_TAP_PAD = 14.dp

/** Wording chosen 2026-10-30. */
private fun updateText(update: UpdateNotice): String = when (update) {
    UpdateNotice.Available -> "A new version is available"
    is UpdateNotice.Downloading -> "Downloading new version · ${(update.progress * 100).toInt()}%"
    UpdateNotice.Ready -> "Restart to finish updating"
}

/** No button chrome; the whole row is the tap target. */
@Composable
private fun MenuItem(
    text: String,
    style: TitleMenuStyle,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    subtitle: String? = null,
    // Done for today
    ticked: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val textColor by animateColorAsState(if (pressed) TitleTextBright else TitleText, tween(120), label = "menuText")

    // Fixed height, so names are evenly spaced with or without a line under them
    Column(
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxWidth()
            .height(MENU_ROW)
            .padding(top = MENU_NAME_TOP)
            .clickable(interactionSource = interaction, indication = null) {
                onMenuClick()
                onClick()
            },
    ) {
        when (style) {
            TitleMenuStyle.SERIF -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = text, style = titleStyle(textColor))
                    if (ticked) Tick()
                }
                subtitle?.let {
                    Text(
                        text = it,
                        fontFamily = Cinzel,
                        fontSize = 10.sp,
                        letterSpacing = 0.12.em,
                        color = TitleText.copy(alpha = 0.55f),
                        maxLines = 1,
                        modifier = Modifier.offset(y = SUBTITLE_LIFT),
                    )
                }
            }
            TitleMenuStyle.SANS -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = text, fontFamily = SansTitle, fontSize = 20.sp, letterSpacing = 0.03.em, color = textColor)
                    if (ticked) Tick()
                }
                subtitle?.let {
                    Text(
                        text = it,
                        fontFamily = SansText,
                        fontSize = 12.sp,
                        letterSpacing = 0.02.em,
                        color = TitleText.copy(alpha = 0.6f),
                        maxLines = 1,
                        modifier = Modifier.offset(y = SUBTITLE_LIFT),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tick() {
    Spacer(Modifier.width(8.dp))
    Icon(Icons.Rounded.CheckCircle, contentDescription = "Solved", tint = UpdateTint, modifier = Modifier.size(18.dp))
}

@Preview(widthDp = 427, heightDp = 952)
@Composable
fun MainMenuPreview() {
    MahjongTheme {
        MainMenuScreen(
            continueInfo = ContinueInfo("Level 3: Mini Turtle"),
            onPlayLevels = {},
            onChooseConstellation = {},
            onSettings = {},
        )
    }
}

// A difficulty, or any; a random layout is dealt at once
@Composable
private fun QuickGamePicker(onPick: (Difficulty?) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLargeIncreased,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
                Text("Quick Game", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Quickly start a game with a random layout",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(22.dp))
                for (row in Difficulty.entries.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (d in row) BigButton(d.displayName, onClick = { onPick(d) }, compact = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                BigButton("Any difficulty", onClick = { onPick(null) }, primary = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
