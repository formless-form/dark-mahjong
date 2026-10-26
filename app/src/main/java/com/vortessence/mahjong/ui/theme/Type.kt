package com.vortessence.mahjong.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.vortessence.mahjong.R

// Title and title-screen menu: Roman inscriptional capitals (lowercase renders as small capitals)
@OptIn(ExperimentalTextApi::class)
val Cinzel = FontFamily(
    listOf(600, 700).map { weight ->
        Font(R.font.cinzel, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

// Seal script (篆書), subset to the four characters of the name seal 暗夜麻將
val SealScript = FontFamily(Font(R.font.seal_script_name, FontWeight.Normal))

// Everything else: Google Sans Flex, Android's own typeface since Material 3 Expressive. The
// bundled file keeps its weight and optical-size axes. Android doesn't pick an optical size by
// itself, so there are three families, each tuned to a band of text sizes.
@OptIn(ExperimentalTextApi::class)
private fun googleSansFlex(opticalSize: Float) = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { weight ->
        Font(
            R.font.google_sans_flex,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    },
)

val SansText = googleSansFlex(opticalSize = 14f)
val SansTitle = googleSansFlex(opticalSize = 22f)
val SansDisplay = googleSansFlex(opticalSize = 40f)

private val Baseline = Typography()

private fun TextStyle.text() = copy(fontFamily = SansText)
private fun TextStyle.title() = copy(fontFamily = SansTitle)
private fun TextStyle.display() = copy(fontFamily = SansDisplay)

// The Material 3 Expressive type scale (sizes, weights and the emphasized set), in Google Sans Flex
val Typography = Typography(
    displayLarge = Baseline.displayLarge.display(),
    displayMedium = Baseline.displayMedium.display(),
    displaySmall = Baseline.displaySmall.display(),
    headlineLarge = Baseline.headlineLarge.display(),
    headlineMedium = Baseline.headlineMedium.title(),
    headlineSmall = Baseline.headlineSmall.title(),
    titleLarge = Baseline.titleLarge.title(),
    titleMedium = Baseline.titleMedium.text(),
    titleSmall = Baseline.titleSmall.text(),
    bodyLarge = Baseline.bodyLarge.text(),
    bodyMedium = Baseline.bodyMedium.text(),
    bodySmall = Baseline.bodySmall.text(),
    labelLarge = Baseline.labelLarge.text(),
    labelMedium = Baseline.labelMedium.text(),
    labelSmall = Baseline.labelSmall.text(),
    displayLargeEmphasized = Baseline.displayLargeEmphasized.display(),
    displayMediumEmphasized = Baseline.displayMediumEmphasized.display(),
    displaySmallEmphasized = Baseline.displaySmallEmphasized.display(),
    headlineLargeEmphasized = Baseline.headlineLargeEmphasized.display(),
    headlineMediumEmphasized = Baseline.headlineMediumEmphasized.title(),
    headlineSmallEmphasized = Baseline.headlineSmallEmphasized.title(),
    titleLargeEmphasized = Baseline.titleLargeEmphasized.title(),
    titleMediumEmphasized = Baseline.titleMediumEmphasized.text(),
    titleSmallEmphasized = Baseline.titleSmallEmphasized.text(),
    bodyLargeEmphasized = Baseline.bodyLargeEmphasized.text(),
    bodyMediumEmphasized = Baseline.bodyMediumEmphasized.text(),
    bodySmallEmphasized = Baseline.bodySmallEmphasized.text(),
    labelLargeEmphasized = Baseline.labelLargeEmphasized.text(),
    labelMediumEmphasized = Baseline.labelMediumEmphasized.text(),
    labelSmallEmphasized = Baseline.labelSmallEmphasized.text(),
)
