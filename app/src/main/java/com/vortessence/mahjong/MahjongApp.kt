package com.vortessence.mahjong

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortessence.mahjong.ui.screens.ConstellationSelectScreen
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.screens.LevelSelectScreen
import com.vortessence.mahjong.ui.screens.MainMenuScreen
import com.vortessence.mahjong.ui.screens.SettingsScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import com.vortessence.mahjong.ui.viewmodel.MahjongViewModel
import com.vortessence.mahjong.ui.viewmodel.Screen

@Composable
fun MahjongApp(viewModel: MahjongViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val levelIndex by viewModel.currentLevelIndex.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val continueInfo by viewModel.continueInfo.collectAsStateWithLifecycle()
    val tutorialStep by viewModel.tutorialStep.collectAsStateWithLifecycle()
    val winMessage by viewModel.winMessage.collectAsStateWithLifecycle()

    MahjongTheme(tableSurface = settings.tableSurface) {
        Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
            // In landscape the camera cutout sits on a long edge; every screen but the title
            // (whose scenery runs full-bleed) keeps its content clear of it
            val contentModifier = if (screen is Screen.MainMenu) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.displayCutout)
            }
            Box(modifier = contentModifier) {
                when (screen) {
                    is Screen.MainMenu -> {
                        MainMenuScreen(
                            continueInfo = continueInfo,
                            onContinue = { viewModel.continueGame() },
                            onPlayLevels = { viewModel.navigateTo(Screen.LevelSelect) },
                            onChooseConstellation = { viewModel.navigateTo(Screen.ConstellationSelect) },
                            onSettings = { viewModel.navigateTo(Screen.Settings) },
                            onMenuClick = { viewModel.audioManager.playMenuClick() },
                        )
                    }

                    is Screen.LevelSelect -> {
                        LevelSelectScreen(
                            progress = progress,
                            levels = viewModel.currentLevelConstellations,
                            onLevelSelected = { level -> viewModel.startLevel(level) },
                            onBack = { viewModel.navigateTo(Screen.MainMenu) },
                        )
                    }

                    is Screen.ConstellationSelect -> {
                        ConstellationSelectScreen(
                            progress = progress,
                            onConstellationSelected = { constellation -> viewModel.startConstellation(constellation) },
                            onBack = { viewModel.navigateTo(Screen.MainMenu) },
                        )
                    }

                    is Screen.Game -> {
                        GameScreen(
                            gameState = gameState,
                            levelIndex = levelIndex,
                            settings = settings,
                            tutorialStep = tutorialStep,
                            winMessage = winMessage,
                            totalLevels = viewModel.currentLevelConstellations.size,
                            onBack = { viewModel.navigateTo(Screen.MainMenu) },
                            onTileClick = { tileId -> viewModel.onTileTapped(tileId) },
                            onTileMatch = { viewModel.playTileMatch() },
                            onHint = { viewModel.requestHint() },
                            onUndo = { viewModel.undo() },
                            onRestart = { viewModel.restartGame() },
                            onReshuffle = { viewModel.reshuffle() },
                            onNextLevel = { viewModel.playNextLevel() },
                            onChooseConstellation = { viewModel.navigateTo(Screen.ConstellationSelect) },
                            onMainMenu = { viewModel.navigateTo(Screen.MainMenu) },
                            onTutorialContinue = { viewModel.advanceTutorial() },
                            onSettingsChanged = { updated -> viewModel.updateSettings(updated) },
                        )
                    }

                    is Screen.Settings -> {
                        SettingsScreen(
                            settings = settings,
                            onSettingsChanged = { updated -> viewModel.updateSettings(updated) },
                            onBack = { viewModel.navigateTo(Screen.MainMenu) },
                        )
                    }
                }
            }
        }
    }
}
