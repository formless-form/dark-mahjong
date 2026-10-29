package com.vortessence.mahjong.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.TutorialAction
import com.vortessence.mahjong.engine.TutorialStep
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import com.vortessence.mahjong.ui.components.GameHeader
import com.vortessence.mahjong.ui.components.GameToolbar
import com.vortessence.mahjong.ui.components.LoadingPage
import com.vortessence.mahjong.ui.components.levelLabel
import com.vortessence.mahjong.ui.components.WinCelebration
import com.vortessence.mahjong.ui.components.MahjongBoard
import com.vortessence.mahjong.ui.components.StalemateDialog
import com.vortessence.mahjong.ui.components.TutorialCard

@Composable
fun GameScreen(
    gameState: GameState?,
    levelIndex: Int?,
    settings: GameSettings = GameSettings(),
    tutorialStep: TutorialStep? = null,
    winMessage: String? = null,
    totalLevels: Int = ConstellationRepository.totalLevels,
    onBack: () -> Unit,
    onTileClick: (Int) -> Unit,
    onTileMatch: () -> Unit = {},
    onHint: () -> Unit,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    onReshuffle: () -> Unit,
    onNextLevel: () -> Unit,
    onChooseConstellation: () -> Unit,
    onMainMenu: () -> Unit,
    onTutorialContinue: () -> Unit = {},
    onSettingsChanged: (GameSettings) -> Unit = {},
    onBoardZoom: (Boolean) -> Unit = {},
) {
    if (gameState == null) {
        LoadingPage(Modifier.background(MaterialTheme.colorScheme.background))
        return
    }

    val hasNextLevel = (levelIndex != null) && (levelIndex < totalLevels)

    val tutorialHighlights = when {
        tutorialStep == null -> emptySet()
        tutorialStep.highlightFreeTiles -> gameState.tiles.filter { it.isFree }.map { it.id }.toSet()
        else -> tutorialStep.targetTileIds
    }

    // The tutorial's zoom steps: each new step is checked against the zoom as it stands, so one
    // already done (zoomed in before the pinch step) passes at once. The pinch step lets the
    // zoom settle a moment first, so its card doesn't change in the middle of the pinch
    var boardZoomedIn by remember { mutableStateOf(false) }
    LaunchedEffect(tutorialStep, boardZoomedIn) {
        if (tutorialStep?.action == TutorialAction.PINCH_ZOOM && boardZoomedIn) delay(700.milliseconds)
        onBoardZoom(boardZoomedIn)
    }

    // When the board is cleared it softly blurs behind the celebration, as Android 17 blurs
    // what sits behind its own panels
    val blur by animateDpAsState(
        targetValue = if (gameState.isWon) 14.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "BoardBlur",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier)
    ) {
        GameHeader(
            gameState = gameState,
            levelIndex = levelIndex,
            onBackClick = onBack,
        )

        // The table runs on to the bottom of the screen, under the toolbar (or tutorial card) and
        // the navigation bar; the tiles are fitted above them
        Box(modifier = Modifier.weight(1f)) {
            val density = LocalDensity.current
            var overlay by remember { mutableIntStateOf(0) }
            MahjongBoard(
                gameState = gameState,
                settings = settings,
                highlightedTileIds = tutorialHighlights,
                highlightZoomOut = tutorialStep?.highlightZoomOut == true,
                onZoomedInChange = { boardZoomedIn = it },
                onTileClick = onTileClick,
                onTileMatch = onTileMatch,
                bottomOverlap = with(density) { overlay.toDp() },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { overlay = it.height }
                    .navigationBarsPadding()
            ) {
                if (tutorialStep != null) {
                    TutorialCard(
                        step = tutorialStep,
                        settings = settings,
                        onContinue = onTutorialContinue,
                        onSettingsChanged = onSettingsChanged
                    )
                } else {
                    GameToolbar(
                        gameState = gameState,
                        onHintClick = onHint,
                        onUndoClick = onUndo,
                        onRestartClick = onRestart,
                    )
                }
            }
        }
    }

    // Fireworks and a warm message when the board is cleared
    if (gameState.isWon) {
        WinCelebration(
            message = winMessage,
            subtitle = listOfNotNull(levelLabel(levelIndex), gameState.constellation.name).distinct().joinToString(" · "),
            hasNextLevel = hasNextLevel,
            animationsEnabled = settings.animationsEnabled,
            onNextLevel = onNextLevel,
            onPlayAgain = onRestart,
            onMainMenu = onMainMenu,
        )
    }

    // Stalemate / no more moves dialog
    if (gameState.isStalemate && !gameState.isWon) {
        StalemateDialog(
            canUndo = gameState.moveHistory.isNotEmpty(),
            onReshuffle = onReshuffle,
            onUndo = onUndo,
            onRestart = onRestart
        )
    }
}
