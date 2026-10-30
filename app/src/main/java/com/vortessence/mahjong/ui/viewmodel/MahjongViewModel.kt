package com.vortessence.mahjong.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vortessence.mahjong.audio.AudioManager
import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.ContinueInfo
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.model.SavedGameState
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.data.repository.PreferencesRepository
import com.vortessence.mahjong.engine.BoardSolvabilityGenerator
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.engine.TileTapResult
import com.vortessence.mahjong.audio.FireRoom
import com.vortessence.mahjong.engine.TutorialScript
import com.vortessence.mahjong.engine.TutorialStep
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

sealed class Screen {
    data object MainMenu : Screen()
    data object LevelSelect : Screen()
    data object ConstellationSelect : Screen()
    data object Game : Screen()
    data object Settings : Screen()
}

class MahjongViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    val audioManager = AudioManager(application)

    // Dark Mahjong is portrait-only, so levels use upstream's tall, phone-shaped layouts
    // (most mahjong layouts are taller than wide); the wide classics stay under Layouts
    private val useWideLayouts = false

    val currentLevelConstellations: List<Constellation>
        get() = ConstellationRepository.getLevelConstellations(useWideLayouts)

    private val _currentScreen = MutableStateFlow<Screen>(Screen.MainMenu)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _currentLevelIndex = MutableStateFlow<Int?>(null)
    val currentLevelIndex: StateFlow<Int?> = _currentLevelIndex.asStateFlow()

    // Index into TutorialScript.steps while the guided tutorial runs, null otherwise
    private val _tutorialStepIndex = MutableStateFlow<Int?>(null)
    val tutorialStep: StateFlow<TutorialStep?> = _tutorialStepIndex
        .map { index -> index?.let { TutorialScript.steps.getOrNull(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val currentTutorialStep: TutorialStep?
        get() = _tutorialStepIndex.value?.let { TutorialScript.steps.getOrNull(it) }

    private val _settings = MutableStateFlow(GameSettings())
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    // Congratulations for the board just cleared; null until it has been chosen
    private val _winMessage = MutableStateFlow<String?>(null)
    val winMessage: StateFlow<String?> = _winMessage.asStateFlow()

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _savedGame = MutableStateFlow<SavedGameState?>(null)
    @Suppress("unused")
    val savedGame: StateFlow<SavedGameState?> = _savedGame.asStateFlow()

    val continueInfo: StateFlow<ContinueInfo?> = combine(
        _gameState,
        _savedGame,
        _currentLevelIndex,
    ) { state, saved, levelIdx ->
        if (state != null && !state.isWon && (state.remainingTilesCount > 0)) {
            val title = if (levelIdx != null) "Level $levelIdx: ${state.constellation.name}" else state.constellation.name
            val subtitle = "${state.remainingTilesCount} tiles remaining"
            ContinueInfo(title = title, subtitle = subtitle)
        } else if (saved != null && saved.tiles.any { !it.isRemoved }) {
            val constellation = ConstellationRepository.getById(saved.constellationId)
            val level = savedLevelIndex(saved)
            val title = if (level != null) "Level $level: ${constellation.name}" else constellation.name
            val remaining = saved.tiles.count { !it.isRemoved }
            val subtitle = "$remaining tiles remaining"
            ContinueInfo(title = title, subtitle = subtitle)
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Collect settings
        viewModelScope.launch {
            prefsRepo.settingsFlow.distinctUntilChanged().collect { s ->
                _settings.value = s
                audioManager.updateSettings(s)
            }
        }
        // The fireplace is heard at the table, and softly on the menu screens
        viewModelScope.launch {
            currentScreen.collect { screen ->
                audioManager.setFireRoom(
                    when (screen) {
                        is Screen.Game -> FireRoom.TABLE
                        is Screen.Settings, is Screen.LevelSelect, is Screen.ConstellationSelect -> FireRoom.MENU
                        else -> FireRoom.NONE
                    },
                )
            }
        }
        // Collect progress
        viewModelScope.launch {
            prefsRepo.progressFlow.collect { p ->
                _progress.value = p
            }
        }
        // Collect saved game
        viewModelScope.launch {
            prefsRepo.savedGameFlow.collect { saved ->
                _savedGame.value = saved
            }
        }
    }

    fun navigateTo(screen: Screen) {
        if (screen != Screen.Game) {
            saveCurrentGame()
        }
        _currentScreen.value = screen
    }

    // A level game saved before the levels were reordered keeps its layout; its number follows it
    private fun savedLevelIndex(saved: SavedGameState): Int? =
        saved.levelIndex?.let { ConstellationRepository.levelIndexOf(saved.constellationId, useWideLayouts) ?: it }

    fun continueGame() {
        val current = _gameState.value
        if (current != null && !current.isWon && current.remainingTilesCount > 0) {
            _currentScreen.value = Screen.Game
            return
        }
        val saved = _savedGame.value
        if (saved != null) {
            val restored = saved.toGameState()
            if (!restored.isWon && restored.remainingTilesCount > 0) {
                _currentLevelIndex.value = savedLevelIndex(saved)
                _gameState.value = restored
                _tutorialStepIndex.value = TutorialScript.resumeStep(restored, saved.tutorialStep)
                _currentScreen.value = Screen.Game
            }
        }
    }

    fun startLevel(levelIndex: Int) {
        _currentLevelIndex.value = levelIndex
        val constellation = ConstellationRepository.getLevel(levelIndex, useWideLayouts)
        initGame(constellation)
        _currentScreen.value = Screen.Game
    }

    fun startConstellation(constellation: Constellation) {
        _currentLevelIndex.value = null
        initGame(constellation)
        _currentScreen.value = Screen.Game
    }

    /**
     * Test builds only (BuildConfig.PERF_HOOKS): the same deal of [layoutName] every time, with the
     * fireplace lit or not on [surface], so frame timings before and after a change compare alike.
     */
    fun startPerfScene(layoutName: String, fireplace: Boolean, surface: TableSurface?, autoplay: Boolean = false) {
        val constellation = ConstellationRepository.constellations.firstOrNull { it.name.equals(layoutName, ignoreCase = true) } ?: return
        // From the saved settings, which may not have loaded yet (a scene opens with the app), so
        // that only the fire and the table change
        viewModelScope.launch {
            val saved = prefsRepo.settingsFlow.first()
            prefsRepo.updateSettings(saved.copy(fireplaceLit = fireplace, tableSurface = surface ?: saved.tableSurface))
        }
        _currentLevelIndex.value = null
        initGame(constellation, Random(7))
        _currentScreen.value = Screen.Game
        perfAutoplay?.cancel()
        // Matches a free pair every two seconds, so match animations can be timed alike
        if (autoplay) perfAutoplay = viewModelScope.launch {
            delay(3000.milliseconds)
            while (true) {
                val state = _gameState.value ?: break
                val (a, b) = MahjongGameEngine.findAvailablePairs(state.tiles).firstOrNull() ?: break
                onTileTapped(a)
                delay(250.milliseconds)
                onTileTapped(b)
                delay(1750.milliseconds)
            }
        }
    }

    private var perfAutoplay: Job? = null

    private fun initGame(constellation: Constellation, random: Random? = null) {
        val fresh = MahjongGameEngine.startNewGame(constellation)
        val state = if (random == null) fresh else {
            val tiles = MahjongGameEngine.recalculateFreedom(BoardSolvabilityGenerator.generateSolvableBoard(constellation, random))
            fresh.copy(tiles = tiles, availablePairsCount = MahjongGameEngine.findAvailablePairs(tiles).size)
        }
        _winMessage.value = null
        _gameState.value = state
        _tutorialStepIndex.value = if (TutorialScript.isTutorial(state)) 0 else null
        saveCurrentGame()
    }

    private fun saveCurrentGame() {
        val current = _gameState.value ?: return
        if (current.isWon || current.remainingTilesCount == 0) return
        val step = if (TutorialScript.isTutorial(current)) _tutorialStepIndex.value ?: SavedGameState.FREE_PLAY else null
        val saved = SavedGameState.fromGameState(current, _currentLevelIndex.value, step)
        _savedGame.value = saved
        viewModelScope.launch {
            prefsRepo.saveGame(saved)
        }
    }

    private fun clearSavedGame() {
        _savedGame.value = null
        viewModelScope.launch {
            prefsRepo.clearSavedGame()
        }
    }

    fun onTileTapped(tileId: Int) {
        val current = _gameState.value ?: return
        if (current.isWon) return

        val step = currentTutorialStep
        if (step != null && !TutorialScript.isTapAllowed(step, tileId)) return

        val (newState, result) = MahjongGameEngine.handleTileTap(current, tileId)
        _gameState.value = newState

        if (step != null && TutorialScript.isStepComplete(step, result, newState)) {
            advanceTutorial()
        }

        when (result) {
            is TileTapResult.Blocked -> {
                audioManager.playTileBlocked()
                viewModelScope.launch {
                    delay(350.milliseconds)
                    if (_gameState.value?.blockedTileId == result.tileId) {
                        _gameState.value = _gameState.value?.copy(blockedTileId = null)
                    }
                }
            }
            is TileTapResult.Selected -> {
                audioManager.playTileSelect()
            }
            is TileTapResult.Deselected -> {
                audioManager.playTileDeselect()
            }
            is TileTapResult.SelectionChanged -> {
                audioManager.playTileSelect()
            }
            is TileTapResult.Matched -> {
                audioManager.playTileSelect()
                if (!_settings.value.animationsEnabled) {
                    audioManager.playTileMatch()
                }
                if (result.isWon) {
                    clearSavedGame()
                    val levelIndex = _currentLevelIndex.value
                    viewModelScope.launch {
                        prefsRepo.recordGameWon(
                            constellationId = current.constellation.id,
                            levelIndex = levelIndex,
                        )
                        _winMessage.value = prefsRepo.nextCongratsMessage(
                            isTutorial = TutorialScript.isTutorial(current),
                        )
                    }
                } else {
                    saveCurrentGame()
                }
            }
        }
    }

    fun playTileMatch() {
        audioManager.playTileMatch()
    }

    /** The celebration has appeared over a cleared board (not one fading out behind the menu). */
    fun playWin() {
        if (_currentScreen.value == Screen.Game) audioManager.playWin()
    }

    /** The board was zoomed in or back out: the tutorial's zoom steps wait on this. */
    fun onBoardZoom(zoomedIn: Boolean) {
        val step = currentTutorialStep ?: return
        if (TutorialScript.isZoomStepComplete(step, zoomedIn)) advanceTutorial()
    }

    /**
     * The button on the tutorial card showing [step]: it moves on only from that step, so a second
     * tap on a card already fading out can't skip the one after.
     */
    fun continueTutorial(step: TutorialStep) {
        if (step == currentTutorialStep) advanceTutorial()
    }

    private fun advanceTutorial() {
        val index = _tutorialStepIndex.value ?: return
        val state = _gameState.value ?: return
        _tutorialStepIndex.value = TutorialScript.nextStep(index, state)
        // Saved with the step, so Continue picks up where the player was
        saveCurrentGame()
    }

    fun undo() {
        // Undo and hints would get in the way of the guided tutorial steps
        if (_tutorialStepIndex.value != null) return
        val current = _gameState.value ?: return
        // A cleared board stays cleared: Replay or Play again deal afresh
        if (current.isWon) return
        if (current.moveHistory.isNotEmpty()) {
            val updated = MahjongGameEngine.undo(current)
            _gameState.value = updated
            saveCurrentGame()
            audioManager.playTileSelect()
        }
    }

    fun requestHint() {
        if (_tutorialStepIndex.value != null) return
        val current = _gameState.value ?: return
        val updated = MahjongGameEngine.requestHint(current)
        _gameState.value = updated
        audioManager.playTileSelect()
    }

    fun restartGame() {
        val current = _gameState.value ?: return
        initGame(current.constellation)
    }

    fun reshuffle() {
        val current = _gameState.value ?: return
        val updated = MahjongGameEngine.reshuffleRemaining(current)
        _gameState.value = updated
        saveCurrentGame()
        audioManager.playTileSelect()
    }

    fun playNextLevel() {
        val currentLevel = _currentLevelIndex.value ?: return
        if (currentLevel < ConstellationRepository.getTotalLevels(useWideLayouts)) {
            startLevel(currentLevel + 1)
        }
    }

    fun updateSettings(newSettings: GameSettings) {
        viewModelScope.launch {
            prefsRepo.updateSettings(newSettings)
        }
    }

    fun onResume() {
        audioManager.onResume()
    }

    fun onPause() {
        saveCurrentGame()
        audioManager.onPause()
    }

    override fun onCleared() {
        super.onCleared()
        audioManager.release()
    }
}
