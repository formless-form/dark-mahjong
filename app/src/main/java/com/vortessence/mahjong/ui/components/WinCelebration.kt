package com.vortessence.mahjong.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vortessence.mahjong.ui.theme.TomorrowMedium
import kotlinx.coroutines.delay

/**
 * Shown over the board when it is cleared: a fireworks light show, then a quiet card with a warm
 * message and big buttons. With animations off, the card appears straight away and there are no
 * fireworks.
 */
@Composable
fun WinCelebration(
    message: String?,
    subtitle: String,
    hasNextLevel: Boolean,
    animationsEnabled: Boolean,
    onNextLevel: () -> Unit,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit,
) {
    var showCard by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            delay(1100)
            showCard = true
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f)),
    ) {
        if (animationsEnabled) {
            FireworksShow(modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = showCard,
            enter = fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.96f),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.widthIn(min = 360.dp, max = 460.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                ) {
                    Text(
                        text = message ?: " ",
                        fontFamily = TomorrowMedium,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        fontFamily = TomorrowMedium,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (hasNextLevel) {
                            CelebrationButton("Next", primary = true, onClick = onNextLevel)
                            CelebrationButton("Replay", onClick = onPlayAgain)
                        } else {
                            CelebrationButton("Play again", primary = true, onClick = onPlayAgain)
                        }
                        CelebrationButton("Menu", onClick = onMainMenu)
                    }
                }
            }
        }
    }
}

@Composable
private fun CelebrationButton(text: String, onClick: () -> Unit, primary: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (primary) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.7f),
        contentColor = if (primary) colors.onPrimaryContainer else colors.onSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (primary) 0.14f else 0.06f)),
        modifier = Modifier.height(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 26.dp)) {
            Text(text = text, fontFamily = TomorrowMedium, fontSize = 18.sp)
        }
    }
}
