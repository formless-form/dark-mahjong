package com.vortessence.mahjong.ui.components

import com.vortessence.mahjong.R
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.floor
import kotlin.math.roundToInt
import com.vortessence.mahjong.tips.TipOffer

/**
 * The tip jar: the tea-drinking monk, a line from Gareth, and the tips at the prices Google Play
 * gives; once one goes through, a thank-you in its place.
 */
@Composable
fun TipCard(offers: List<TipOffer>, thanked: Boolean, onTip: (TipOffer) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = MaterialTheme.shapes.extraLargeIncreased,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
                // The tea-drinking monk: a 3D render in a 192 px pixel-art style, kept crisp (no smoothing).
                // Both pictures share one frame, so he stays put when the cat appears.
                Box(Modifier.size(MONK_DP.dp)) {
                    Image(
                        bitmap = ImageBitmap.imageResource(if (thanked) R.drawable.tip_monk_thanks else R.drawable.tip_monk),
                        contentDescription = if (thanked) "A smiling monk with his tea, and a cat asleep beside him"
                            else "A smiling monk enjoying a cup of tea",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.matchParentSize(),
                    )
                    if (thanked) SleepyZs(Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(18.dp))
                if (thanked) {
                    Text(
                        "Thank you! It means a lot.",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(24.dp))
                    BigButton("Close", onClick = onClose, primary = true, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        "If you'd like to tip me for my work, please do!",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(24.dp))
                    if (offers.isEmpty()) {
                        // Google Play hasn't priced the tips (offline, or Play still starting up)
                        Text(
                            "Tips can't be reached right now. Please check your connection and try again later.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            for (offer in offers) {
                                BigButton(offer.price, onClick = { onTip(offer) }, primary = true, compact = true, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Payments handled by Google Play",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The monk pictures are 168 art pixels square, drawn one art pixel per dp. */
private const val MONK_DP = 168
private const val ART_PX = 168f

/** Where the zzz rise from, just above the sleeping cat's ear, in art pixels. */
private val ZZZ_START = Offset(101f, 134f)
private val ZZZ_FILL = Color(0xFFDBAE93)
private val ZZZ_OUTLINE = Color.Black.copy(alpha = 0.47f)

/**
 * Three pixel-art Z's drifting up and to the right from the cat, growing from 5 to 7 art pixels and
 * fading as they go. They move in whole art pixels so they stay in step with the picture.
 */
@Composable
private fun SleepyZs(modifier: Modifier) {
    val phase by rememberInfiniteTransition(label = "zzz").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "zzz phase",
    )
    Canvas(modifier) {
        val px = size.width / ART_PX
        for (k in 0 until 3) {
            val t = (phase + k / 3f) % 1f
            val alpha = when {
                t < 0.15f -> t / 0.15f
                t > 0.7f -> (1f - t) / 0.3f
                else -> 1f
            }
            val glyph = 5 + (t * 3f).toInt().coerceAtMost(2)
            val x = ZZZ_START.x + floor(t * 13f)
            val y = ZZZ_START.y - floor(t * 20f) - glyph
            drawPixelZ(x * px, y * px, glyph, px, alpha)
        }
    }
}

/** A Z [n] art pixels square: full top and bottom bars joined by a two-pixel diagonal. */
private fun DrawScope.drawPixelZ(left: Float, top: Float, n: Int, px: Float, alpha: Float) {
    val cells = buildList {
        for (c in 0 until n) { add(c to 0); add(c to n - 1) }
        for (r in 1 until n - 1) {
            val c = ((n - 1) * (n - 1 - r).toFloat() / (n - 1)).roundToInt()
            add(c to r)
            if (c - 1 >= 0) add(c - 1 to r)
        }
    }
    val edge = px * 0.4f
    for ((c, r) in cells) {
        drawRect(ZZZ_OUTLINE.copy(alpha = ZZZ_OUTLINE.alpha * alpha),
            Offset(left + c * px - edge, top + r * px - edge), Size(px + 2 * edge, px + 2 * edge))
    }
    for ((c, r) in cells) {
        drawRect(ZZZ_FILL.copy(alpha = alpha), Offset(left + c * px, top + r * px), Size(px, px))
    }
}
