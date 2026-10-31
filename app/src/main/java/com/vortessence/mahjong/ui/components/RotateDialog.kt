package com.vortessence.mahjong.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Asked when a wide board comes up and the player hasn't yet said how to show one: turn the phone
 * on its side, or keep it upright. The answer is kept (Settings, Wide boards).
 */
@Composable
fun RotateDialog(
    animate: Boolean,
    onLandscape: () -> Unit,
    onPortrait: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        BlurBehindDialog()
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH).fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                RotatePhoneIcon(animate = animate, modifier = Modifier.size(96.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Rotate your phone?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "This is a wide board – would you like to play in landscape mode?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BigButton("Play in landscape", onClick = onLandscape, primary = true, icon = PhoneLandscape, modifier = Modifier.fillMaxWidth())
                    BigButton("Stay in portrait", onClick = onPortrait, icon = PhonePortrait, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * A phone turning onto its side between two curved arrows, over and over; held on its side while
 * the phone's Remove animations is on. Drawn in a 120-unit square.
 */
@Composable
fun RotatePhoneIcon(animate: Boolean, modifier: Modifier = Modifier) {
    val phone = MaterialTheme.colorScheme.secondary
    val arrows = MaterialTheme.colorScheme.primary
    var angle = 90f
    var arrowAlpha = 1f
    if (animate) {
        val cycle = rememberInfiniteTransition(label = "RotatePhone")
        val turn = CubicBezierEasing(0.6f, 0f, 0.3f, 1f)
        angle = cycle.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = TURN_CYCLE
                    0f at (TURN_CYCLE * 0.22f).toInt() using turn
                    90f at (TURN_CYCLE * 0.48f).toInt()
                    90f at (TURN_CYCLE * 0.78f).toInt() using turn
                },
                RepeatMode.Restart,
            ),
            label = "PhoneAngle",
        ).value
        // The arrows brighten as the phone turns
        arrowAlpha = cycle.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = TURN_CYCLE
                    0.25f at (TURN_CYCLE * 0.18f).toInt() using LinearEasing
                    1f at (TURN_CYCLE * 0.30f).toInt()
                    1f at (TURN_CYCLE * 0.48f).toInt() using LinearEasing
                    0.25f at (TURN_CYCLE * 0.62f).toInt()
                },
                RepeatMode.Restart,
            ),
            label = "ArrowAlpha",
        ).value
    }
    Canvas(modifier) {
        val unit = size.minDimension / 120f
        val line = Stroke(width = 4.5f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (start in ARROW_STARTS) drawArrow(start, arrows.copy(alpha = arrows.alpha * arrowAlpha), line, unit)
        rotate(angle, pivot = Offset(60f * unit, 60f * unit)) {
            drawRoundRect(
                color = phone,
                topLeft = Offset(45f * unit, 30f * unit),
                size = Size(30f * unit, 60f * unit),
                cornerRadius = CornerRadius(6f * unit),
                style = line,
            )
            drawLine(phone, Offset(56f * unit, 82f * unit), Offset(64f * unit, 82f * unit), line.width, StrokeCap.Round)
        }
    }
}

// An arc around the phone, clockwise from [start] degrees, ending in an open head. The head lines up
// with the way the arc travels over its last stretch (not the tangent at the tip), so its two sides
// look even on the curve
private fun DrawScope.drawArrow(start: Float, color: Color, line: Stroke, unit: Float) {
    val r = ARROW_RADIUS * unit
    val c = 60f * unit
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = ARROW_SWEEP,
        useCenter = false,
        topLeft = Offset(c - r, c - r),
        size = Size(r * 2, r * 2),
        style = line,
    )
    val head = HEAD_LENGTH * unit
    val end = Math.toRadians((start + ARROW_SWEEP).toDouble())
    val back = end - HEAD_LENGTH / ARROW_RADIUS
    val tip = Offset(c + r * cos(end).toFloat(), c + r * sin(end).toFloat())
    val from = Offset(c + r * cos(back).toFloat(), c + r * sin(back).toFloat())
    val length = hypot(tip.x - from.x, tip.y - from.y)
    // Pointing back along the arrow, from the tip
    val ux = (from.x - tip.x) / length
    val uy = (from.y - tip.y) / length
    fun barb(spread: Double) = Offset(
        tip.x + head * (ux * cos(spread) - uy * sin(spread)).toFloat(),
        tip.y + head * (ux * sin(spread) + uy * cos(spread)).toFloat(),
    )
    val spread = Math.toRadians(HEAD_SPREAD)
    val path = Path().apply {
        barb(spread).let { moveTo(it.x, it.y) }
        lineTo(tip.x, tip.y)
        barb(-spread).let { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = line)
}

private const val TURN_CYCLE = 3200
private const val ARROW_RADIUS = 48f
private const val ARROW_SWEEP = 52f
private val ARROW_STARTS = floatArrayOf(-72f, 108f)
private const val HEAD_LENGTH = 11f
private const val HEAD_SPREAD = 35.0

/** The turning phone's own outline, upright: for the "Stay in portrait" button. */
val PhonePortrait: ImageVector by lazy { phoneIcon(landscape = false) }

/** The turning phone's own outline, on its side: for the "Play in landscape" button. */
val PhoneLandscape: ImageVector by lazy { phoneIcon(landscape = true) }

// A 24-unit icon: a 12 x 20 phone with rounded corners and a short line for its speaker
private fun phoneIcon(landscape: Boolean): ImageVector {
    // Points of the upright phone, turned a quarter clockwise about the middle for landscape
    fun p(x: Float, y: Float) = if (landscape) Offset(24f - y, x) else Offset(x, y)
    val outline = SolidColor(Color.Black)
    return ImageVector.Builder(
        name = if (landscape) "PhoneLandscape" else "PhonePortrait",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(stroke = outline, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            if (landscape) roundRect(2f, 6f, 20f, 12f, 2.4f) else roundRect(6f, 2f, 12f, 20f, 2.4f)
        }
        path(stroke = outline, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            p(10.6f, 18.2f).let { moveTo(it.x, it.y) }
            p(13.4f, 18.2f).let { lineTo(it.x, it.y) }
        }
    }.build()
}

private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x + w, y + r)
    lineTo(x + w, y + h - r)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x + w - r, y + h)
    lineTo(x + r, y + h)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x, y + h - r)
    lineTo(x, y + r)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x + r, y)
    close()
}
