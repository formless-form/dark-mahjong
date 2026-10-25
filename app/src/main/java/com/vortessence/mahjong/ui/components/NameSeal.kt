package com.vortessence.mahjong.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.ui.theme.SealScript

private val SealInk = Color(0xFF8E2A23)
private val SealPaper = Color(0xFFF2E7D8)

/**
 * The app's name seal: 暗夜麻將 ("dark-night mahjong") in seal script, cut white out of deep
 * vermilion like a carved stone chop. Read right column first, top to bottom: 暗夜 then 麻將.
 * An AGSL pass roughens the edges slightly and knocks a few flecks out of the ink, as a real
 * impression would.
 */
@Composable
fun NameSeal(modifier: Modifier = Modifier, size: Dp = 70.dp) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val wear = if (LocalInspectionMode.current) null else remember(density.density) {
        RuntimeShader(SEAL_WEAR_AGSL).let { shader ->
            shader.setFloatUniform("dp", density.density)
            RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
    }

    Canvas(
        modifier
            .size(size)
            .graphicsLayer { renderEffect = wear },
    ) {
        val s = this.size.minDimension
        drawRoundRect(SealInk, cornerRadius = CornerRadius(s * 0.07f))
        val style = TextStyle(fontFamily = SealScript, color = SealPaper, fontSize = with(density) { (s * 0.40f).toSp() })
        // Quadrant centres; columns read right to left
        val cells = listOf(
            "暗" to Offset(0.71f, 0.31f), "夜" to Offset(0.71f, 0.70f),
            "麻" to Offset(0.29f, 0.31f), "將" to Offset(0.29f, 0.70f),
        )
        for ((ch, c) in cells) {
            val layout = measurer.measure(ch, style)
            drawText(
                layout,
                topLeft = Offset(c.x * s - layout.size.width / 2f, c.y * s - layout.size.height / 2f),
            )
        }
    }
}

// Samples the seal with a small noise-driven displacement (rough carved edges) and removes
// scattered flecks where low-frequency noise peaks (worn ink). Sizes are in dp so the wear
// looks the same at any density.
private const val SEAL_WEAR_AGSL = """
uniform shader content;
uniform float dp;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), u.x),
               mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), u.x), u.y);
}

half4 main(float2 p) {
    float2 q = p / dp;
    float2 d = float2(vnoise(q * 0.9) - 0.5, vnoise(q * 0.9 + 17.0) - 0.5) * 1.1 * dp;
    half4 c = content.eval(p + d);
    float n = vnoise(q * 0.22) * 0.65 + vnoise(q * 0.7 + 5.0) * 0.35;
    float keep = 1.0 - smoothstep(0.70, 0.76, n);
    return c * keep;
}
"""
