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

    // Dark Mahjong is portrait-only, so levels use upstream's tall, phone-shaped layouts
    // (most mahjong layouts are taller than wide); the wide classics stay under Layouts
    val currentLevelConstellations: List<Constellation>
        get() = ConstellationRepository.levelConstellations

    private val _currentScreen = MutableStateFlow<Screen>(Screen.MainMenu)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _currentLevelIndex = MutableStateFlow<Int?>(null)
    val currentLevelIndex: StateFlow<Int?> = _currentLevelIndex.asStateFlow()

    // The day (an epoch day) whose Today's Puzzle is on the table, null for any other board
    private val _puzzleDay = MutableStateFlow<Long?>(null)
    val puzzleDay: StateFlow<Long?> = _puzzleDay.asStateFlow()

    // Today's Puzzle's day (an epoch day), kept current: moved on at 4 am, and looked at again
    // whenever the app comes back (a delay doesn't run on while the phone sleeps)
    private val _today = MutableStateFlow(TodaysPuzzle.today())
    val today: StateFlow<Long> = _today.asStateFlow()

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

    // This win was the player's first clean clear: its card introduces the gold ribbon
    private val _firstRibbon = MutableStateFlow(false)
    val firstRibbon: StateFlow<Boolean> = _firstRibbon.asStateFlow()

    // The first-game performance check, as stored (null until read)
    private val _perfCheck = MutableStateFlow<PerfCheckState?>(null)
    // A test build's perf scene is on the table: never part of the check
    private val _perfScene = MutableStateFlow(false)

    /**
     * Whether the frames now being drawn count towards the first-game check: a real game in play
     * with a full board's worth of tiles standing (never the tutorial, a small board any phone
     * draws smoothly, which would pass a phone that lags on a full one), the check still to run,
     * and the effects on (Low-performance mode and the phone's Remove animations both off).
     */
    val measuringFrames: StateFlow<Boolean> = combine(
        _perfCheck, _currentScreen, _gameState, _settings,
        combine(_perfScene, _tutorialStepIndex) { perfScene, tutorial -> perfScene || tutorial != null },
    ) { check, screen, game, s, excluded ->
        check?.check == PerfCheck.PENDING && screen is Screen.Game && game != null && !game.isWon &&
            game.remainingTilesCount >= PERF_CHECK_MIN_TILES && !s.lowPerformance && s.phoneAnimates && !excluded
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * The board on the table was dealt (or carried on) since the check's verdict and hasn't been
     * touched: the question may open over it, as the board comes up, but never while it is being
     * looked at or played, under a finger about to tap.
     */
    private val _boardFresh = MutableStateFlow(true)
    val boardFresh: StateFlow<Boolean> = _boardFresh.asStateFlow()

    /** A new deal (or the same one again): counted, so the board starts afresh (its zoom too). */
    private val _deal = MutableStateFlow(0)
    val deal: StateFlow<Int> = _deal.asStateFlow()

    private fun newDeal() {
        stopFinishing()
        _deal.value++
        _boardFresh.value = true
    }

    /**
     * The fire's shadows traced lighter (FireShadows), by itself: on a phone the check found near
     * the line, or struggling (before its question is answered, and after a no thanks). A phone
     * with a decent margin keeps the full trace.
     */
    val lightFireShadows: StateFlow<Boolean> = _perfCheck
        .map { it?.check == PerfCheck.PASSED_CLOSE || it?.check == PerfCheck.SLOW || it?.check == PerfCheck.DECLINED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The check found this phone struggling: the question of Low-performance mode is due. */
    val perfQuestion: StateFlow<Boolean> = _perfCheck
        .map { it?.check == PerfCheck.SLOW }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _savedGame = MutableStateFlow<SavedGameState?>(null)
    // The play clock, for each deal's best-clear time: it runs while a game in play is on the table
    // and the app is in front, and stops whenever the board is away (a menu, Settings, another
    // app). [clockSince] is when it last started (elapsedRealtime; 0 while stopped), for the deal
    // [clockDeal]; time up to then is in GameState.playMillis
    private val _resumed = MutableStateFlow(true)
    private var clockSince = 0L
    private var clockDeal = -1

    /** Time spent playing [state] so far, counting the clock now running. */
    private fun playedMillis(state: GameState): Long =
        state.playMillis + if (clockSince > 0L && clockDeal == _deal.value) SystemClock.elapsedRealtime() - clockSince else 0L

    // Stops the clock, adding the time since it started to the deal it was running for (if that
    // deal is still on the table: a new one has its own)
    private fun settleClock() {
        if (clockSince == 0L) return
        val ran = SystemClock.elapsedRealtime() - clockSince
        clockSince = 0L
        if (clockDeal == _deal.value) _gameState.update { it?.copy(playMillis = it.playMillis + ran) }
    }

    // The game has saved or cleared its save itself since launch (savedGameFlow's collector);
    // declared before init, which starts that collector
    private var savedLocally = false

    /** Today's Puzzle started (a move made) and not yet solved: Continue rather than Play. */
    val todaysPuzzleStarted: StateFlow<Boolean> = combine(_gameState, _puzzleDay, _savedGame, _today) { state, day, saved, today ->
        (day == today && state != null && !state.isWon && state.moveHistory.isNotEmpty()) ||
            (state == null && saved?.puzzleDay == today && saved.moveHistory.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // A day's puzzle left unfinished is gone once its day is over
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

    // The phone's Remove animations (Settings > Accessibility) sets its animator scale to 0; the
    // game's own motion (match bursts, fireworks, the hint pulse, zoom glides) follows it, and
    // changes with it while the game is open
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
        // Levels were reordered by measured difficulty: carry the highest level over, once
        viewModelScope.launch { prefsRepo.migrateLevelOrder() }
        // Hints became earned: players from before start with 2
        viewModelScope.launch { prefsRepo.migrateHints() }
        // The check's verdict and Low-performance mode belong to this phone, not one restored from
        viewModelScope.launch { prefsRepo.namePerfPhone() }
        // Collect settings. While the player's own changes are still being written, what's read
        // back is older than what they see, so it waits until the writes are done
        viewModelScope.launch {
            prefsRepo.settingsFlow.distinctUntilChanged().collect { s ->
                if (settingsWrites == 0) {
                    savedSettings = s
                    showSettings(s)
                }
            }
        }
        // The fireplace is heard at the table, and softly on the menu screens
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
                // A test build's reset is on its way: what's stored is about to go
                if (!perfCheckResetting) _perfCheck.value = it
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
                // Read from storage only until the game first saves or clears it itself: from then
                // on the game's own copy is the newest, and a read back of an earlier write (parsed
                // off the main thread, so it can land late) must not bring back a board just won
                if (!savedLocally) _savedGame.value = saved
            }
        }
        // The play clock follows the board: on the table, in play and in front
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
        // Today's Puzzle moves on at 4 am, even with the app left open on a menu
        viewModelScope.launch {
            while (true) {
                val wait = Duration.between(LocalDateTime.now(), TodaysPuzzle.nextDayStarts(_today.value)).toMillis()
                delay(wait.coerceAtLeast(0L).milliseconds)
                _today.value = TodaysPuzzle.today()
            }
        }
    }

    // Where the game on the table was started from, which Back returns to: Levels, Layouts, or the
    // title (Continue)
    private val _gameOrigin = MutableStateFlow<Screen>(Screen.MainMenu)
    val gameOrigin: StateFlow<Screen> = _gameOrigin.asStateFlow()

    fun navigateTo(screen: Screen) {
        perfAutoplay?.cancel()
        // Leaving for another screen drops a deal still being worked out
        cancelDeal()
        if (screen != Screen.Game) {
            saveCurrentGame()
        }
        _currentScreen.value = screen
    }

    // A level game saved before the levels were reordered keeps its layout; its number follows it
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

    // Also the next level from the win card, which keeps where the first was started from
    private fun beginLevel(levelIndex: Int, origin: Screen? = null) {
        val constellation = ConstellationRepository.getLevel(levelIndex)
        dealThen({ MahjongGameEngine.startNewGame(constellation) }) { state ->
            origin?.let { _gameOrigin.value = it }
            _currentLevelIndex.value = levelIndex
            beginGame(state)
            _currentScreen.value = Screen.Game
        }
    }

    /**
     * Today's Puzzle: carried on if it's on the table or saved unfinished, otherwise dealt (again,
     * once solved: the same deal, ticked only once).
     */
    fun startTodaysPuzzle() {
        perfAutoplay?.cancel()
        cancelDeal()
        val today = TodaysPuzzle.today()
        val current = _gameState.value
        val saved = _savedGame.value
        // Puts [state] on the table (null: carries on with the one there)
        fun open(state: GameState?) {
            _gameOrigin.value = Screen.TodaysPuzzle
            if (state != null) {
                _gameState.value = state
                _winMessage.value = null
                _firstRibbon.value = false
                _tutorialStepIndex.value = null
                newDeal()
            }
            // Today's Puzzle, not a Quick Game: its win card offers no "Another?"
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

    // The last Quick Game's layout, so the next one is different
    private var lastQuickGame: String? = null

    // Playing a Quick Game: its win card offers another, of the same difficulty
    private val _quickGame = MutableStateFlow(false)
    val quickGame: StateFlow<Boolean> = _quickGame.asStateFlow()

    /** Quick Game: a random layout of [difficulty] (any, if null), never the one just played. */
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

    /** From a Quick Game's win card: another random layout as hard as the one just cleared. */
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

    /**
     * Test builds only (BuildConfig.PERF_HOOKS): the same deal of [layoutName] every time, with the
     * fireplace lit or not on [surface], so frame timings before and after a change compare alike.
     */
    fun startPerfScene(layoutName: String, fireplace: Boolean, surface: TableSurface?, autoplay: Boolean = false, countsForCheck: Boolean = false) {
        val constellation = ConstellationRepository.constellations.firstOrNull { it.name.equals(layoutName, ignoreCase = true) } ?: return
        // From the saved settings, which may not have loaded yet (a scene opens with the app), so
        // that only the fire and the table change
        viewModelScope.launch {
            val saved = prefsRepo.settingsFlow.first()
            prefsRepo.updateSettings(saved, saved.copy(fireplaceLit = fireplace, tableSurface = surface ?: saved.tableSurface))
        }
        // Kept out of the first-game check, unless the check is what's being tried
        _perfScene.value = !countsForCheck
        dealThen({ MahjongGameEngine.startNewGame(constellation, Random(7)) }) { state ->
            _currentLevelIndex.value = null
            _gameOrigin.value = Screen.MainMenu
            beginGame(state)
            _currentScreen.value = Screen.Game
            // Matches a free pair every two seconds, so match animations can be timed alike
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

    // A deal being worked out. Dealing can take a moment (a layout that opens with few free pairs
    // has each opening checked, BoardSolvabilityGenerator.openingIsSafe), so it's done off the main
    // thread, and nothing changes until it's ready: the screen the player is on stays responsive.
    // A newer deal, or leaving for another screen or game, drops it
    private var dealJob: Job? = null

    private fun cancelDeal() {
        dealJob?.cancel()
        dealJob = null
    }

    /** Works out a new game with [make] off the main thread, then hands it to [start] on it. */
    private fun dealThen(make: () -> GameState, start: (GameState) -> Unit) {
        perfAutoplay?.cancel()
        cancelDeal()
        val job = viewModelScope.launch {
            val state = withContext(dealContext) { make() }
            dealJob = null
            start(state)
        }
        // Dealt inline (tests), it's already done
        if (job.isActive) dealJob = job
    }

    /** A fresh deal on the table, nothing chosen yet, and saved. */
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
        // With the time played up to now, the clock still running
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

    // Finishing a board whose tiles are all free: the pairs are taken one by one, as taps would
    private val _finishing = MutableStateFlow(false)
    val finishing: StateFlow<Boolean> = _finishing.asStateFlow()
    private var finishJob: Job? = null

    // A new deal stops a finish still taking the old one's pairs
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
                    // A tile the player had picked is put down first
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
        // The player's taps wait while the board finishes itself
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
                // One clear at a time, so an earlier tap's can't cut this flash short
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
                // Without the smash there's no moment of contact to play it at
                if (!_settings.value.matchSmash) {
                    audioManager.playTileMatch()
                }
                if (result.isWon) {
                    clearSavedGame()
                    val levelIndex = _currentLevelIndex.value
                    val day = _puzzleDay.value
                    // The best-clear record (none for the tutorial, whose hints are free)
                    val record = if (TutorialScript.isTutorial(current)) null else LevelRecord(
                        // From the board as it is now: the win just stopped the clock (its collector
                        // runs at once) and added its time there, not to newState
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
                            clean = !newState.usedHelp && !TutorialScript.isTutorial(current),
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
            val updated = MahjongGameEngine.undo(current).copy(undosUsed = current.undosUsed + 1)
            _gameState.value = updated
            saveCurrentGame()
            audioManager.playTileSelect()
        }
    }

    private var hintSearch: Job? = null
    // The tiles the search under way is for
    private var hintSearchTiles: List<BoardTile>? = null

    fun requestHint() {
        if (_tutorialStepIndex.value != null) return
        val current = _gameState.value ?: return
        if (current.isWon) return
        _boardFresh.value = false
        // The pair already shown is shown again, free; a new one costs a hint (the screen
        // doesn't ask with none left). The tutorial's free play gives them free, as it promises
        if (current.hintPair != null) return
        val free = TutorialScript.isTutorial(current)
        if (!free && _progress.value.hints <= 0) return
        // A second tap while the same board's hint is still being found is the same request
        if (hintSearch?.isActive == true && hintSearchTiles === current.tiles) return
        audioManager.playTileSelect()
        // Finding a plan can take a moment on a big board, so it's done in the background, and
        // shown only if the board hasn't changed meanwhile
        hintSearch?.cancel()
        hintSearchTiles = current.tiles
        hintSearch = viewModelScope.launch {
            val hint = withContext(Dispatchers.Default) { MahjongGameEngine.findHint(current) } ?: return@launch
            // Shown first, then paid for: a hint is never spent on a board that changed while it
            // was being found (nothing suspends between this check and the hint appearing)
            val state = _gameState.value
            if (state == null || state.tiles !== current.tiles) return@launch
            _gameState.value = state.copy(
                hintPair = hint.pair, plan = hint.plan.ifEmpty { state.plan }, blockedTileId = null, usedHelp = true,
                hintsUsed = state.hintsUsed + 1,
            )
            if (free) return@launch
            // Paid for even if a new deal comes meanwhile: it was seen
            val paid = withContext(NonCancellable) { prefsRepo.spendHint() }
            // None left after all (two boards' requests racing for the last one): taken back
            // (and the board no longer counts as helped, so a clean clear keeps its ribbon)
            if (!paid) _gameState.update { s ->
                if (s?.hintPair == hint.pair) s.copy(hintPair = null, usedHelp = state.usedHelp, hintsUsed = state.hintsUsed) else s
            }
        }
    }

    fun restartGame() {
        val current = _gameState.value ?: return
        // Today's Puzzle is one deal: starting again is that deal again
        if (_puzzleDay.value != null) {
            replayDeal()
        } else {
            dealThen({ MahjongGameEngine.startNewGame(current.constellation) }) { beginGame(it) }
        }
    }

    /** Restart with the same deal: the tiles back as they were first laid out. */
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

    // The settings as last written (or read), which each change is told apart from; and how many
    // writes are on their way
    private var savedSettings = GameSettings()
    private var settingsWrites = 0

    private fun showSettings(s: GameSettings) {
        // Low-performance mode stills everything the phone's Remove animations does
        val shown = s.copy(animationsEnabled = phoneAnimates && !s.lowPerformance, phoneAnimates = phoneAnimates)
        _settings.value = shown
        audioManager.updateSettings(shown)
    }

    /** A setting changed: shown and heard at once, and only what changed is saved. */
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

    /** A slider being dragged: shown and heard as it moves, saved when it's let go. */
    fun previewSettings(newSettings: GameSettings) {
        showSettings(newSettings)
    }

    /** The Fire sound slider is being dragged, so the fire is heard at table loudness. */
    fun auditionFire(on: Boolean) {
        audioManager.auditionFire(on)
    }

    /** The slider is let go: what it shows is saved. */
    fun commitSettings() {
        updateSettings(_settings.value)
    }

    /** The first-game check is done: a phone that kept up is left alone; one that didn't gets its card. */
    fun onFrameVerdict(verdict: FrameVerdict) {
        if (_perfCheck.value?.check != PerfCheck.PENDING) return
        val state = when {
            verdict.struggling -> PerfCheck.SLOW
            verdict.close -> PerfCheck.PASSED_CLOSE
            else -> PerfCheck.PASSED
        }
        val stutterPercent = (verdict.stutterShare * 100).roundToInt()
        _perfCheck.value = PerfCheckState(state, stutterPercent)
        // Reached on the board being played: the question waits for the next
        if (verdict.struggling) _boardFresh.value = false
        viewModelScope.launch { prefsRepo.setPerfCheck(state, stutterPercent) }
    }

    /** The question is answered: Low-performance mode on if [turnOn], otherwise left as it is. */
    fun perfQuestionAnswered(turnOn: Boolean) {
        val state = if (turnOn) PerfCheck.SWITCHED else PerfCheck.DECLINED
        if (turnOn) updateSettings(_settings.value.copy(lowPerformance = true))
        _perfCheck.value = _perfCheck.value?.copy(check = state)
        viewModelScope.launch { prefsRepo.setPerfCheck(state) }
    }

    // A test build's reset of the check is being written
    private var perfCheckResetting = false

    /** Test builds: the check as on a first install, with nothing stored shown meanwhile. */
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
        /** Where new deals are worked out: off the main thread; tests deal inline (EmptyCoroutineContext). */
        @Volatile
        internal var dealContext: CoroutineContext = Dispatchers.Default
    }
}

// The first-game check counts frames only while at least this many tiles stand: a full board's
// work, not the last few pairs of one
private const val PERF_CHECK_MIN_TILES = 60

/** What Today's Puzzle is called wherever it's named. */
const val TODAYS_PUZZLE = "Today's Puzzle"

// Auto-finish: the pause between a pair's two picks, and between pairs (long enough for each
// match's animation to be seen)
private val FINISH_PICK = 140.milliseconds
private val FINISH_STEP = 420.milliseconds
