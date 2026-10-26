package com.vortessence.mahjong.ui.screens

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vortessence.mahjong.R
import com.vortessence.mahjong.data.model.ContinueInfo
import com.vortessence.mahjong.ui.components.NameSeal
import com.vortessence.mahjong.ui.components.rememberFilmGrain
import com.vortessence.mahjong.ui.theme.Cinzel
import com.vortessence.mahjong.ui.theme.MahjongTheme
import com.vortessence.mahjong.ui.theme.SansText
import com.vortessence.mahjong.ui.theme.SansTitle

private val TitleText = Color(0xFFE6DFD1)
private val TitleTextBright = Color(0xFFFFF7E8)
private val Shade = Color(0xFF080C0B)

// The background photo is CC0, so no credit is required; this one is a courtesy (also in NOTICE.md)
private const val PHOTO_CREDIT = "Photo: Huangdan2060 · CC0 · Wikimedia Commons"

/**
 * Title screen (portrait): a misty mountain photograph. The name seal and title sit in the open
 * sky at the top; a short text menu in the same
 * inscriptional capitals sits over the dark forest at the bottom.
 */
@Composable
fun MainMenuScreen(
    continueInfo: ContinueInfo? = null,
    menuStyle: TitleMenuStyle = TitleMenuStyle.SERIF,
    onContinue: () -> Unit = {},
    onPlayLevels: () -> Unit,
    onChooseConstellation: () -> Unit,
    onSettings: () -> Unit,
    onMenuClick: () -> Unit = {},
) {
    val grain = rememberFilmGrain()
    Box(modifier = Modifier.fillMaxSize().background(Shade)) {
        // Crop scales uniformly to fill, so the photo is never stretched; film grain on top
        Image(
            painter = painterResource(R.drawable.title_mountains),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { renderEffect = grain },
        )
        // Darken the bright sky behind the title, and deepen the forest behind the menu
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
            Spacer(Modifier.height(14.dp))
            Text(
                text = "DARK MAHJONG",
                style = when (menuStyle) {
                    TitleMenuStyle.SERIF -> titleStyle(TitleText, size = 19.sp)
                    TitleMenuStyle.SANS -> titleStyle(TitleText, size = 20.sp).copy(fontWeight = FontWeight.Bold)
                },
            )
        }

        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(start = 40.dp, end = 40.dp, bottom = 64.dp)
                .fillMaxWidth(),
        ) {
            if (continueInfo != null) {
                MenuItem("Continue", menuStyle, subtitle = continueInfo.title, onMenuClick = onMenuClick, onClick = onContinue)
            }
            MenuItem("Play", menuStyle, onMenuClick = onMenuClick, onClick = onPlayLevels)
            MenuItem("Layouts", menuStyle, onMenuClick = onMenuClick, onClick = onChooseConstellation)
            MenuItem("Settings", menuStyle, onMenuClick = onMenuClick, onClick = onSettings)
        }

        // System sans (Google Sans / Roboto) renders crispest at this size; the extra 30 px keeps
        // the end of the line clear of the screen's rounded corner
        val cornerClearance = with(LocalDensity.current) { 30.toDp() }
        Text(
            text = PHOTO_CREDIT,
            fontFamily = FontFamily.SansSerif,
            fontSize = 6.5.sp,
            letterSpacing = 0.02.em,
            color = TitleText.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(end = 10.dp + cornerClearance, bottom = 3.dp),
        )
    }
}

/**
 * How the title screen's menu is set: in the title's Cinzel capitals, or (the alternative under
 * consideration) in Google Sans Flex like the rest of the app, with a bolder, larger title.
 */
enum class TitleMenuStyle { SERIF, SANS }

private fun titleStyle(color: Color, size: TextUnit = 17.sp) = TextStyle(
    fontFamily = Cinzel,
    fontSize = size,
    letterSpacing = 0.32.em,
    color = color,
)

/**
 * A plain text menu row with no button chrome. The whole row is the tap target; it brightens
 * while pressed and plays a soft click when chosen.
 */
@Composable
private fun MenuItem(
    text: String,
    style: TitleMenuStyle,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    subtitle: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val textColor by animateColorAsState(if (pressed) TitleTextBright else TitleText, tween(120), label = "menuText")

    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 60.dp else 52.dp)
            .clickable(interactionSource = interaction, indication = null) {
                onMenuClick()
                onClick()
            },
    ) {
        when (style) {
            TitleMenuStyle.SERIF -> {
                Text(text = text, style = titleStyle(textColor))
                subtitle?.let {
                    Text(
                        text = it,
                        fontFamily = Cinzel,
                        fontSize = 10.sp,
                        letterSpacing = 0.12.em,
                        color = TitleText.copy(alpha = 0.55f),
                        maxLines = 1,
                    )
                }
            }
            TitleMenuStyle.SANS -> {
                Text(text = text, fontFamily = SansTitle, fontSize = 20.sp, letterSpacing = 0.03.em, color = textColor)
                subtitle?.let {
                    Text(
                        text = it,
                        fontFamily = SansText,
                        fontSize = 12.sp,
                        letterSpacing = 0.02.em,
                        color = TitleText.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 427, heightDp = 952)
@Composable
fun MainMenuPreview() {
    MahjongTheme {
        MainMenuScreen(
            continueInfo = ContinueInfo("Level 3: Mini Turtle", "40 tiles remaining"),
            onPlayLevels = {},
            onChooseConstellation = {},
            onSettings = {},
        )
    }
}
