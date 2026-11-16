package com.vortessence.mahjong.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A back gesture's progress, read by [predictiveBack] while drawing. */
@Stable
class PredictiveBackState {
    /** 0 at rest to 1 as the gesture is dragged in. */
    var progress by mutableFloatStateOf(0f)

    /** [BackEventCompat.EDGE_LEFT] or [BackEventCompat.EDGE_RIGHT]. */
    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)

    /** 0 to 1 as the screen fades once let go. */
    var exit by mutableFloatStateOf(0f)

    /** Set on completion, so the screen swap that follows doesn't animate again. */
    var committed = false

    /** True while the screen behind should be drawn. */
    val revealing by derivedStateOf { progress > 0f || exit > 0f }

    fun reset() {
        progress = 0f
        exit = 0f
        committed = false
    }
}

/**
 * Android 17 predictive back: the screen shrinks, drifts toward the gesture's edge and rounds like
 * a card over the screen behind; let go and it fades, cancel and it springs back.
 */
@Composable
fun PredictiveBackHandling(enabled: Boolean, state: PredictiveBackState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                state.swipeEdge = event.swipeEdge
                state.progress = event.progress
            }
            animate(0f, 1f, animationSpec = tween(durationMillis = 150)) { value, _ -> state.exit = value }
            state.committed = true
            onBack()
        } catch (e: CancellationException) {
            val from = state.progress
            scope.launch {
                animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> state.progress = value }
            }
            throw e
        }
    }
}

/** Draws [PredictiveBackHandling]'s effect; nothing at rest. */
fun Modifier.predictiveBack(state: PredictiveBackState): Modifier = graphicsLayer {
    val progress = state.progress
    val exit = state.exit
    if (progress == 0f && exit == 0f) return@graphicsLayer
    val scale = 1f - 0.1f * progress
    scaleX = scale
    scaleY = scale
    val direction = if (state.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
    translationX = direction * (size.width / 20f - 8.dp.toPx()) * progress
    alpha = 1f - exit
    shape = RoundedCornerShape(32.dp * progress)
    clip = true
}
