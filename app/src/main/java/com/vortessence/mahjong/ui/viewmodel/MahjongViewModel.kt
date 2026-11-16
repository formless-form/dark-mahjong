package com.vortessence.mahjong.ui.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vortessence.mahjong.BuildConfig
import com.vortessence.mahjong.audio.AudioManager
import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.Difficulty
import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.ContinueInfo
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.LevelRecord
import com.vortessence.mahjong.data.model.PerfCheck
import com.vortessence.mahjong.data.model.PerfCheckState
import com.vortessence.mahjong.perf.FrameVerdict
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.model.SavedGameState
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.TileSet
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.data.repository.PreferencesRepository
import com.vortessence.mahjong.data.repository.levelLabel
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.engine.TileTapResult
import com.vortessence.mahjong.audio.FireRoom
import com.vortessence.mahjong.engine.TodaysPuzzle
import com.vortessence.mahjong.engine.TutorialScript
import com.vortessence.mahjong.engine.TutorialStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

sealed class Screen {
    data object MainMenu : Screen()
    data object LevelSelect : Screen()
    data object ConstellationSelect : Screen()
    data object TodaysPuzzle : Screen()
    data object Game : Screen()
    data object Settings : Screen()
}

class MahjongViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    val audioManager = AudioManager(application)

    // Portrait-only, so levels use upstream's tall layouts; the wide classics stay under Layouts
    val currentLevelConstellations: List<Constellation>
        get() = ConstellationRepository.levelConstellations

    private val _currentScreen = MutableStateFlow<Screen>(Screen.MainMenu)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _currentLevelIndex = MutableStateFlow<Int?>(null)
    val currentLevelIndex: StateFlow<Int?> = _currentLevelIndex.asStateFlow()

    // Epoch day of the Today's Puzzle on the table; null for any other board
    private val _puzzleDay = MutableStateFlow<Long?>(null)
    val puzzleDay: StateFlow<Long?> = _puzzleDay.asStateFlow()

    // Moved on at 4 am, and rechecked on resume (a delay doesn't run while the phone sleeps)
    private val _today = MutableStateFlow(TodaysPuzzle.today())
    val today: StateFlow<Long> = _today.asStateFlow()

    // Into TutorialScript.steps; null outside the guided tutorial
    private val _tutorialStepIndex = MutableStateFlow<Int?>(null)
    val tutorialStep: StateFlow<TutorialStep?> = _tutorialStepIndex
        .map { index -> index?.let { TutorialScript.steps.getOrNull(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val currentTutorialStep: TutorialStep?
        get() = _tutorialStepIndex.value?.let { TutorialScript.steps.getOrNull(it) }

    private val _settings = MutableStateFlow(GameSettings())
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    // Null until chosen
    private val _winMessage = MutableStateFlow<String?>(null)
    val winMessage: StateFlow<String?> = _winMessage.asStateFlow()

    // First clean clear: its card introduces the gold ribbon
    private val _firstRibbon = MutableStateFlow(false)
    val firstRibbon: StateFlow<Boolean> = _firstRibbon.asStateFlow()

    // Null until read
    private val _perfCheck = MutableStateFlow<PerfCheckState?>(null)
    // Test build's perf scene: never part of the check
    private val _perfScene = MutableStateFlow(false)

    /**
     * Frames count towards the first-game check only for a real game with a full board standing
     * (small boards like the tutorial would pass a phone that lags on a full one) and effects on.
     */
    val measuringFrames: StateFlow<Boolean> = combine(
        _perfCheck, _currentScreen, _gameState, _settings,
        combine(_perfScene, _tutorialStepIndex) { perfScene, tutorial -> perfScene || tutorial != null },
    ) { check, screen, game, s, excluded ->
        check?.check == PerfCheck.PENDING && screen is Screen.Game && game != null && !game.isWon &&
            game.remainingTilesCount >= PERF_CHECK_MIN_TILES && !s.lowPerformance && s.phoneAnimates && !excluded
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Untouched since dealt or carried on: the check's question may open only then, never under a
     * finger about to tap.
     */
    private val _boardFresh = MutableStateFlow(true)
    val boardFresh: StateFlow<Boolean> = _boardFresh.asStateFlow()

    /** Bumped per deal (or replay), so the board and its zoom start afresh. */
    private val _deal = MutableStateFlow(0)
    val deal: StateFlow<Int> = _deal.asStateFlow()

    private fun newDeal() {
        stopFinishing()
        _deal.value++
        _boardFresh.value = true
    }

    /** Lighter FireShadows trace on a phone the check found near the line or struggling. */
    val lightFireShadows: StateFlow<Boolean> = _perfCheck
        .map { it?.check == PerfCheck.PASSED_CLOSE || it?.check == PerfCheck.SLOW || it?.check == PerfCheck.DECLINED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Struggling: the Low-performance mode question is due. */
    val perfQuestion: StateFlow<Boolean> = _perfCheck
        .map { it?.check == PerfCheck.SLOW }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _savedGame = MutableStateFlow<SavedGameState?>(null)
    // Play clock for best-clear times: runs only while a game in play is on the table and in front.
    // [clockSince]: elapsedRealtime it started (0 stopped), for [clockDeal]; earlier time is in
    // GameState.playMillis
    private val _resumed = MutableStateFlow(true)
    private var clockSince = 0L
    private var clockDeal = -1

    /** Including the clock now running. */
    private fun playedMillis(state: GameState): Long =
        state.playMillis + if (clockSince > 0L && clockDeal == _deal.value) SystemClock.elapsedRealtime() - clockSince else 0L

    // Credits the run to its deal only if that deal is still on the table
    private fun settleClock() {
        if (clockSince == 0L) return
        val ran = SystemClock.elapsedRealtime() - clockSince
        clockSince = 0L
        if (clockDeal == _deal.value) _gameState.update { it?.copy(playMillis = it.playMillis + ran) }
    }

    // Saved or cleared locally since launch; declared before init, which starts savedGameFlow's collector
    private var savedLocally = false

    /** A move made and not yet solved: Continue rather than Play. */
    val todaysPuzzleStarted: StateFlow<Boolean> = combine(_gameState, _puzzleDay, _savedGame, _today) { state, day, saved, today ->
        (day == today && state != null && !state.isWon && state.moveHistory.isNotEmpty()) ||
            (state == null && saved?.puzzleDay == today && saved.moveHistory.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun puzzleGone(day: Long?, today: Long = TodaysPuzzle.today()) = day != null && day != today

    val continueInfo: StateFlow<ContinueInfo?> = combine(
        _gameState,
        _savedGame,
        _currentLevelIndex,
        _puzzleDay,
        _today,
    ) { state, saved, levelIdx, day, today ->
        if (state != null && !state.isWon && (state.remainingTilesCount > 0) && !puzzleGone(day, today)) {
            ContinueInfo(title = if (day != null) TODAYS_PUZZLE else continueTitle(levelIdx, state.constellation.name))
        } else if (saved != null && saved.tiles.any { !it.isRemoved } && !puzzleGone(saved.puzzleDay, today)) {
            ConstellationRepository.getById(saved.constellationId)?.let { constellation ->
                ContinueInfo(title = if (saved.puzzleDay != null) TODAYS_PUZZLE else continueTitle(savedLevelIndex(saved), constellation.name))
            }
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Remove animations (Accessibility) sets animator scale to 0; the game's motion follows it live
    private val resolver = application.contentResolver
    private fun readPhoneAnimates() =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    private var phoneAnimates = readPhoneAnimates()
    private val animationScaleObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            phoneAnimates = readPhoneAnimates()
            showSettings(_settings.value)
        }
    }.also {
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, it)
    }

    // "Level 9: Heart", or just "Tutorial"
    private fun continueTitle(level: Int?, name: String): String =
        listOfNotNull(levelLabel(level), name).distinct().joinToString(": ")

    init {
        // Levels were reordered by measured difficulty
        viewModelScope.launch { prefsRepo.migrateLevelOrder() }
        // Hints became earned: earlier players start with 2
        viewModelScope.launch { prefsRepo.migrateHints() }
        // Verdict and Low-performance mode belong to this phone, not one restored from
        viewModelScope.launch { prefsRepo.namePerfPhone() }
        // Ignored while the player's own writes are pending: what's read back is older
        viewModelScope.launch {
            prefsRepo.settingsFlow.distinctUntilChanged().collect { s ->
                if (settingsWrites == 0) {
                    savedSettings = s
                    showSettings(s)
                }
            }
        }
        viewModelScope.launch {
            currentScreen.collect { screen ->
                audioManager.setFireRoom(
                    when (screen) {
                        is Screen.Game -> FireRoom.TABLE
                        is Screen.Settings, is Screen.LevelSelect, is Screen.ConstellationSelect, is Screen.TodaysPuzzle -> FireRoom.MENU
                        else -> FireRoom.NONE
                    },
                )
            }
        }
        viewModelScope.launch {
            prefsRepo.perfCheckFlow.collect {
                if (BuildConfig.PERF_HOOKS) android.util.Log.i("FrameWatch", "check: $it")
                // A test build's reset is on its way
                if (!perfCheckResetting) _perfCheck.value = it
            }
        }
        viewModelScope.launch {
            prefsRepo.progressFlow.collect { p ->
                _progress.value = p
            }
        }
        viewModelScope.launch {
            prefsRepo.savedGameFlow.collect { saved ->
                // Only until the game first saves itself: a late read-back of an earlier write must
                // not bring back a board just won
                if (!savedLocally) _savedGame.value = saved
            }
        }
        viewModelScope.launch {
            combine(
                _currentScreen, _resumed, _deal, _gameState.map { it != null && !it.isWon }.distinctUntilChanged(),
            ) { screen, resumed, _, inPlay -> screen is Screen.Game && resumed && inPlay }
                .collect { running ->
                    settleClock()
                    if (running) {
                        clockSince = SystemClock.elapsedRealtime()
                        clockDeal = _deal.value
                    }
                }
        }
        // Even with the app left open on a menu
        viewModelScope.launch {
            while (true) {
                val wait = Duration.between(LocalDateTime.now(), TodaysPuzzle.nextDayStarts(_today.value)).toMillis()
                delay(wait.coerceAtLeast(0L).milliseconds)
                _today.value = TodaysPuzzle.today()
            }
        }
    }

    // Where Back returns to from the table
    private val _gameOrigin = MutableStateFlow<Screen>(Screen.MainMenu)
    val gameOrigin: StateFlow<Screen> = _gameOrigin.asStateFlow()

    fun navigateTo(screen: Screen) {
        perfAutoplay?.cancel()
        // Drops a deal still being worked out
        cancelDeal()
        if (screen != Screen.Game) {
            saveCurrentGame()
        }
        _currentScreen.value = screen
    }

    // A save from before the reorder keeps its layout; its number follows it
    private fun savedLevelIndex(saved: SavedGameState): Int? =
        saved.levelIndex?.let { ConstellationRepository.levelIndexOf(saved.constellationId) ?: it }

    fun continueGame() {
        perfAutoplay?.cancel()
        cancelDeal()
        val current = _gameState.value
        if (current != null && !current.isWon && current.remainingTilesCount > 0 && !puzzleGone(_puzzleDay.value)) {
            _gameOrigin.value = Screen.MainMenu
            _currentScreen.value = Screen.Game
            return
        }
        val saved = _savedGame.value
        if (saved != null && !puzzleGone(saved.puzzleDay)) {
            val restored = saved.toGameState()
            if (!restored.isWon && restored.remainingTilesCount > 0) {
                _currentLevelIndex.value = savedLevelIndex(saved)
                _puzzleDay.value = saved.puzzleDay
                _gameState.value = restored
                _tutorialStepIndex.value = TutorialScript.resumeStep(restored, saved.tutorialStep)
                newDeal()
                _gameOrigin.value = Screen.MainMenu
                _currentScreen.value = Screen.Game
            }
        }
    }

    fun startLevel(levelIndex: Int) {
        beginLevel(levelIndex, origin = Screen.LevelSelect)
    }

    // [origin] null (next level from the win card) keeps the first's
    private fun beginLevel(levelIndex: Int, origin: Screen? = null) {
        val constellation = ConstellationRepository.getLevel(levelIndex)
        dealThen({ MahjongGameEngine.startNewGame(constellation) }) { state ->
            origin?.let { _gameOrigin.value = it }
            _currentLevelIndex.value = levelIndex
            beginGame(state)
            _currentScreen.value = Screen.Game
        }
    }

    /** Carried on if on the table or saved unfinished, else dealt (once solved: the same deal again, ticked once). */
    fun startTodaysPuzzle() {
        perfAutoplay?.cancel()
        cancelDeal()
        val today = TodaysPuzzle.today()
        val current = _gameState.value
        val saved = _savedGame.value
        // [state] null carries on with the one there
        fun open(state: GameState?) {
            _gameOrigin.value = Screen.TodaysPuzzle
            if (state != null) {
                _gameState.value = state
                _winMessage.value = null
                _firstRibbon.value = false
                _tutorialStepIndex.value = null
                newDeal()
            }
            // Its win card offers no "Another?"
            _quickGame.value = false
            _currentLevelIndex.value = null
            _puzzleDay.value = today
            saveCurrentGame()
            _currentScreen.value = Screen.Game
        }
        when {
            current != null && _puzzleDay.value == today && !current.isWon && current.remainingTilesCount > 0 -> open(null)
            saved != null && saved.puzzleDay == today && saved.tiles.any { !it.isRemoved } -> open(saved.toGameState())
            else -> dealThen({ TodaysPuzzle.dealOn(today) }) { open(it) }
        }
    }

    private var lastQuickGame: String? = null

    // Its win card offers another of the same difficulty
    private val _quickGame = MutableStateFlow(false)
    val quickGame: StateFlow<Boolean> = _quickGame.asStateFlow()

    /** Random layout of [difficulty] (any if null), never the one just played. */
    fun startQuickGame(difficulty: Difficulty?) {
        val pool = ConstellationRepository.constellations
            .filter { it.id != ConstellationRepository.tutorialConstellation.id }
            .filter { difficulty == null || it.difficulty == difficulty }
        val choice = (pool.filter { it.id != lastQuickGame }.ifEmpty { pool }).randomOrNull() ?: return
        lastQuickGame = choice.id
        dealThen({ MahjongGameEngine.startNewGame(choice) }) { state ->
            _gameOrigin.value = Screen.MainMenu
            _currentLevelIndex.value = null
            beginGame(state)
            _quickGame.value = true
            _currentScreen.value = Screen.Game
        }
    }

    fun anotherQuickGame() {
        startQuickGame(_gameState.value?.constellation?.difficulty)
    }

    fun startConstellation(constellation: Constellation) {
        dealThen({ MahjongGameEngine.startNewGame(constellation) }) { state ->
            _gameOrigin.value = Screen.ConstellationSelect
            _currentLevelIndex.value = null
            beginGame(state)
            _currentScreen.value = Screen.Game
        }
    }

    /** Test builds only (PERF_HOOKS): a fixed deal, so frame timings before and after a change compare. */
    fun startPerfScene(
        layoutName: String, fireplace: Boolean, surface: TableSurface?, autoplay: Boolean = false, countsForCheck: Boolean = false,
        tiles: TileSet? = null, fireIntensity: Float? = null,
    ) {
        val constellation = ConstellationRepository.constellations.firstOrNull { it.name.equals(layoutName, ignoreCase = true) } ?: return
        // From stored settings (may not have loaded yet), so only the fire and table change
        viewModelScope.launch {
            val saved = prefsRepo.settingsFlow.first()
            prefsRepo.updateSettings(
                saved,
                saved.copy(
                    fireplaceLit = fireplace, tableSurface = surface ?: saved.tableSurface,
                    tileSet = tiles ?: saved.tileSet, fireIntensity = fireIntensity ?: saved.fireIntensity,
                ),
            )
        }
        // Out of the first-game check unless that's what's being tried
        _perfScene.value = !countsForCheck
        dealThen({ MahjongGameEngine.startNewGame(constellation, Random(7)) }) { state ->
            _currentLevelIndex.value = null
            _gameOrigin.value = Screen.MainMenu
            beginGame(state)
            _currentScreen.value = Screen.Game
            // A pair every two seconds, so match animations time alike
            if (autoplay) perfAutoplay = viewModelScope.launch {
                delay(3000.milliseconds)
                while (true) {
                    val game = _gameState.value ?: break
                    val (a, b) = MahjongGameEngine.findAvailablePairs(game.tiles).firstOrNull() ?: break
                    onTileTapped(a)
                    delay(250.milliseconds)
                    onTileTapped(b)
                    delay(1750.milliseconds)
                }
            }
        }
    }

    private var perfAutoplay: Job? = null

    // Dealing can be slow (openingIsSafe), so done off the main thread with nothing changing until
    // ready. A newer deal or leaving drops it
    private var dealJob: Job? = null

    private fun cancelDeal() {
        dealJob?.cancel()
        dealJob = null
    }

    /** [make] off the main thread, then [start] on it. */
    private fun dealThen(make: () -> GameState, start: (GameState) -> Unit) {
        perfAutoplay?.cancel()
        cancelDeal()
        val job = viewModelScope.launch {
            val state = withContext(dealContext) { make() }
            dealJob = null
            start(state)
        }
        // Dealt inline (tests): already done
        if (job.isActive) dealJob = job
    }

    private fun beginGame(state: GameState) {
        _puzzleDay.value = null
        _quickGame.value = false
        _winMessage.value = null
        _firstRibbon.value = false
        _gameState.value = state
        _tutorialStepIndex.value = if (TutorialScript.isTutorial(state)) 0 else null
        newDeal()
        saveCurrentGame()
    }

    private fun saveCurrentGame() {
        val current = _gameState.value ?: return
        if (current.isWon || current.remainingTilesCount == 0) return
        val step = if (TutorialScript.isTutorial(current)) _tutorialStepIndex.value ?: SavedGameState.FREE_PLAY else null
        // Including the clock still running
        val saved = SavedGameState.fromGameState(current.copy(playMillis = playedMillis(current)), _currentLevelIndex.value, step, _puzzleDay.value)
        savedLocally = true
        _savedGame.value = saved
        viewModelScope.launch {
            prefsRepo.saveGame(saved)
        }
    }

    private fun clearSavedGame() {
        savedLocally = true
        _savedGame.value = null
        viewModelScope.launch {
            prefsRepo.clearSavedGame()
        }
    }

    // Auto-finish of an all-free board, pair by pair as taps would
    private val _finishing = MutableStateFlow(false)
    val finishing: StateFlow<Boolean> = _finishing.asStateFlow()
    private var finishJob: Job? = null

    private fun stopFinishing() {
        finishJob?.cancel()
        finishJob = null
        _finishing.value = false
    }

    fun finish() {
        val start = _gameState.value ?: return
        if (!start.canFinish || currentTutorialStep != null || finishJob?.isActive == true) return
        _finishing.value = true
        val plan = start.finishPlan ?: return
        finishJob = viewModelScope.launch {
            try {
                for (pair in plan) {
                    val state = _gameState.value ?: break
                    if (state.isWon || _currentScreen.value != Screen.Game) break
                    // Put down the player's picked tile first
                    if (state.selectedTileId != null && state.selectedTileId != pair.first) tap(state.selectedTileId)
                    tap(pair.first)
                    delay(FINISH_PICK)
                    tap(pair.second)
                    delay(FINISH_STEP)
                }
            } finally {
                _finishing.value = false
            }
        }
    }

    fun onTileTapped(tileId: Int) {
        if (_finishing.value) return
        tap(tileId)
    }

    private fun tap(tileId: Int) {
        val current = _gameState.value ?: return
        if (current.isWon) return
        _boardFresh.value = false

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
                // So an earlier tap's clear can't cut this flash short
                blockedClear?.cancel()
                blockedClear = viewModelScope.launch {
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
                // Without the smash there's no contact moment to play it at
                if (!_settings.value.matchSmash) {
                    audioManager.playTileMatch()
                }
                if (result.isWon) {
                    clearSavedGame()
                    val levelIndex = _currentLevelIndex.value
                    val day = _puzzleDay.value
                    // None for the tutorial, whose hints are free
                    val record = if (TutorialScript.isTutorial(current)) null else LevelRecord(
                        // Current board, not newState: the win's clock stop added its time there
                        millis = playedMillis(_gameState.value ?: newState),
                        hints = newState.hintsUsed,
                        reshuffles = newState.reshufflesUsed,
                        undos = newState.undosUsed,
                    )
                    viewModelScope.launch {
                        if (day != null) prefsRepo.recordPuzzleSolved(day)
                        _firstRibbon.value = prefsRepo.recordGameWon(
                            constellationId = current.constellation.id,
                            levelIndex = levelIndex,
                            // Gold ribbon: no hints, reshuffles or undos (Gareth 2026-11-13; undo allowed before 1.4.16)
                            clean = !newState.usedHelp && newState.undosUsed == 0 && !TutorialScript.isTutorial(current),
                            puzzleDay = day,
                            record = record,
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

    private var blockedClear: Job? = null

    fun playTileMatch() {
        audioManager.playTileMatch()
    }

    /** Not for a board fading out behind the menu. */
    fun playWin() {
        if (_currentScreen.value == Screen.Game) audioManager.playWin()
    }

    /** The tutorial's zoom steps wait on this. */
    fun onBoardZoom(zoomedIn: Boolean) {
        val step = currentTutorialStep ?: return
        if (TutorialScript.isZoomStepComplete(step, zoomedIn)) advanceTutorial()
    }

    /** Only from [step], so a second tap on a fading card can't skip the next. */
    fun continueTutorial(step: TutorialStep) {
        if (step == currentTutorialStep) advanceTutorial()
    }

    private fun advanceTutorial() {
        val index = _tutorialStepIndex.value ?: return
        val state = _gameState.value ?: return
        _tutorialStepIndex.value = TutorialScript.nextStep(index, state)
        // So Continue resumes at this step
        saveCurrentGame()
    }

    fun undo() {
        // Would get in the way of the guided steps
        if (_tutorialStepIndex.value != null) return
        val current = _gameState.value ?: return
        // Replay or Play again deal afresh
        if (current.isWon) return
        if (current.moveHistory.isNotEmpty()) {
            val updated = MahjongGameEngine.undo(current).copy(undosUsed = current.undosUsed + 1)
            _gameState.value = updated
            saveCurrentGame()
            audioManager.playTileSelect()
        }
    }

    private var hintSearch: Job? = null
    private var hintSearchTiles: List<BoardTile>? = null

    fun requestHint() {
        if (_tutorialStepIndex.value != null) return
        val current = _gameState.value ?: return
        if (current.isWon) return
        _boardFresh.value = false
        // The pair already shown is free; so are the tutorial's free-play hints, as it promises
        if (current.hintPair != null) return
        val free = TutorialScript.isTutorial(current)
        if (!free && _progress.value.hints <= 0) return
        // Same board still being searched: same request
        if (hintSearch?.isActive == true && hintSearchTiles === current.tiles) return
        audioManager.playTileSelect()
        hintSearch?.cancel()
        hintSearchTiles = current.tiles
        hintSearch = viewModelScope.launch {
            val hint = withContext(Dispatchers.Default) { MahjongGameEngine.findHint(current) } ?: return@launch
            // Shown, then paid for: never spent on a board that changed meanwhile (nothing suspends
            // between this check and the hint appearing)
            val state = _gameState.value
            if (state == null || state.tiles !== current.tiles) return@launch
            _gameState.value = state.copy(
                hintPair = hint.pair, plan = hint.plan.ifEmpty { state.plan }, blockedTileId = null, usedHelp = true,
                hintsUsed = state.hintsUsed + 1,
            )
            if (free) return@launch
            // Even if a new deal comes meanwhile: it was seen
            val paid = withContext(NonCancellable) { prefsRepo.spendHint() }
            // Lost a race for the last hint: taken back, unhelped, so a clean clear keeps its ribbon
            if (!paid) _gameState.update { s ->
                if (s?.hintPair == hint.pair) s.copy(hintPair = null, usedHelp = state.usedHelp, hintsUsed = state.hintsUsed) else s
            }
        }
    }

    fun restartGame() {
        val current = _gameState.value ?: return
        // Today's Puzzle is one deal
        if (_puzzleDay.value != null) {
            replayDeal()
        } else {
            dealThen({ MahjongGameEngine.startNewGame(current.constellation) }) { beginGame(it) }
        }
    }

    fun replayDeal() {
        val current = _gameState.value ?: return
        cancelDeal()
        hintSearch?.cancel()
        perfAutoplay?.cancel()
        _winMessage.value = null
        _firstRibbon.value = false
        val state = MahjongGameEngine.replay(current)
        _gameState.value = state
        _tutorialStepIndex.value = if (TutorialScript.isTutorial(state)) 0 else null
        newDeal()
        saveCurrentGame()
    }

    fun reshuffle() {
        val current = _gameState.value ?: return
        _boardFresh.value = false
        val updated = MahjongGameEngine.reshuffleRemaining(current).copy(usedHelp = true, reshufflesUsed = current.reshufflesUsed + 1)
        _gameState.value = updated
        saveCurrentGame()
        audioManager.playTileSelect()
    }

    fun playNextLevel() {
        val currentLevel = _currentLevelIndex.value ?: return
        if (currentLevel < ConstellationRepository.totalLevels) {
            beginLevel(currentLevel + 1)
        }
    }

    // Last written (or read), to diff each change against; and writes in flight
    private var savedSettings = GameSettings()
    private var settingsWrites = 0

    private fun showSettings(s: GameSettings) {
        // Low-performance mode stills what the phone's Remove animations does
        val shown = s.copy(animationsEnabled = phoneAnimates && !s.lowPerformance, phoneAnimates = phoneAnimates)
        _settings.value = shown
        audioManager.updateSettings(shown)
    }

    /** Shown and heard at once; only what changed is saved. */
    fun updateSettings(newSettings: GameSettings) {
        showSettings(newSettings)
        val from = savedSettings
        savedSettings = newSettings
        settingsWrites++
        viewModelScope.launch {
            try {
                prefsRepo.updateSettings(from, newSettings)
            } finally {
                settingsWrites--
            }
        }
    }

    /** While a slider is dragged: not saved until [commitSettings]. */
    fun previewSettings(newSettings: GameSettings) {
        showSettings(newSettings)
    }

    fun auditionFire(on: Boolean) {
        audioManager.auditionFire(on)
    }

    fun commitSettings() {
        updateSettings(_settings.value)
    }

    fun onFrameVerdict(verdict: FrameVerdict) {
        if (_perfCheck.value?.check != PerfCheck.PENDING) return
        val state = when {
            verdict.struggling -> PerfCheck.SLOW
            verdict.close -> PerfCheck.PASSED_CLOSE
            else -> PerfCheck.PASSED
        }
        val stutterPercent = (verdict.stutterShare * 100).roundToInt()
        _perfCheck.value = PerfCheckState(state, stutterPercent)
        // The question waits for the next board
        if (verdict.struggling) _boardFresh.value = false
        viewModelScope.launch { prefsRepo.setPerfCheck(state, stutterPercent) }
    }

    fun perfQuestionAnswered(turnOn: Boolean) {
        val state = if (turnOn) PerfCheck.SWITCHED else PerfCheck.DECLINED
        if (turnOn) updateSettings(_settings.value.copy(lowPerformance = true))
        _perfCheck.value = _perfCheck.value?.copy(check = state)
        viewModelScope.launch { prefsRepo.setPerfCheck(state) }
    }

    private var perfCheckResetting = false

    /** Test builds: as on a first install, showing nothing stored meanwhile. */
    fun resetPerfCheck() {
        perfCheckResetting = true
        _perfCheck.value = null
        viewModelScope.launch {
            prefsRepo.resetPerfCheck()
            perfCheckResetting = false
            _perfCheck.value = prefsRepo.perfCheckFlow.first()
        }
    }

    fun onResume() {
        _resumed.value = true
        _today.value = TodaysPuzzle.today()
        audioManager.onResume()
    }

    fun onPause() {
        saveCurrentGame()
        _resumed.value = false
        audioManager.onPause()
    }

    override fun onCleared() {
        super.onCleared()
        resolver.unregisterContentObserver(animationScaleObserver)
        audioManager.release()
    }

    companion object {
        /** Tests deal inline (EmptyCoroutineContext). */
        @Volatile
        internal var dealContext: CoroutineContext = Dispatchers.Default
    }
}

// Frames count only while this many tiles stand: a full board's work, not the last pairs
private const val PERF_CHECK_MIN_TILES = 60

const val TODAYS_PUZZLE = "Today's Puzzle"

// Auto-finish pauses: within a pair, and between pairs (long enough to see each match)
private val FINISH_PICK = 140.milliseconds
private val FINISH_STEP = 420.milliseconds
