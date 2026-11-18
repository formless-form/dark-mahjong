package com.vortessence.mahjong.data.repository

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.LevelRecord
import com.vortessence.mahjong.data.model.MAX_HINTS
import com.vortessence.mahjong.data.model.PerfCheck
import com.vortessence.mahjong.data.model.PerfCheckState
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.model.SavedGameState
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.TileSet
import com.vortessence.mahjong.data.model.WideBoards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDateTime
import kotlin.random.Random

// Corrupt file starts afresh rather than crashing every launch
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mahjong_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

// Stored highest level's numbering: 2 = measured order (1.3.7), 3 = Mah's wide boards (1.4.16)
private const val MEASURED_LEVEL_ORDER = 2
private const val WIDE_MAH_LEVEL_ORDER = 3
// Journey at 100 levels
private const val JOURNEY_100_LEVEL_ORDER = 4

// See Keys.PERF_CHECK_VERSION
private const val PERF_CHECK_VERSION = 2

// Maker/model: a new phone of the same model would measure alike
private val thisPhone = "${Build.MANUFACTURER}/${Build.MODEL}"

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val AUTO_ZOOM = booleanPreferencesKey("auto_zoom")
        val HIGHLIGHT_FREE_TILES = booleanPreferencesKey("highlight_free_tiles")
        val VIBRATION = booleanPreferencesKey("vibration")

        val HIGHEST_LEVEL = intPreferencesKey("highest_level")
        val COMPLETED_CONSTELLATIONS = stringSetPreferencesKey("completed_constellations")
        // Epoch days
        val PUZZLE_DAYS_SOLVED = stringSetPreferencesKey("puzzle_days_solved")
        val SAVED_GAME = stringPreferencesKey("saved_game")
        val TABLE_SURFACE = stringPreferencesKey("table_surface")
        val TILE_SET = stringPreferencesKey("tile_set")
        val LAMP_BRIGHTNESS = floatPreferencesKey("lamp_brightness")
        val FIREPLACE = booleanPreferencesKey("fireplace")
        val LOW_PERFORMANCE = booleanPreferencesKey("low_performance")
        val CLEAN_CONSTELLATIONS = stringSetPreferencesKey("clean_constellations")
        // "id=millis,hints,reshuffles,undos" entries
        val LEVEL_RECORDS = stringSetPreferencesKey("level_records")
        val HINTS = intPreferencesKey("hints")
        val FIRE_VOLUME = floatPreferencesKey("fire_volume")
        val FIRE_INTENSITY = floatPreferencesKey("fire_intensity")
        val WIDE_BOARDS = stringPreferencesKey("wide_boards")
        val LAST_WIN_DAY = longPreferencesKey("last_win_epoch_day")
        // *_LEVEL_ORDER above; absent = the original order
        val LEVEL_ORDER = intPreferencesKey("level_order")
        // PerfCheck's name, and the stutter percent
        val PERF_CHECK = stringPreferencesKey("perf_check")
        val PERF_CHECK_STUTTERS = intPreferencesKey("perf_check_stutters")
        // Phone the check and mode belong to: backups carry them to a new phone, where the check reruns
        val PERF_DEVICE = stringPreferencesKey("perf_device")
        // 2 since it tells near-the-line from a margin (1.4.13); older passes are rechecked once
        val PERF_CHECK_VERSION = intPreferencesKey("perf_check_version")
        fun congratsBag(pool: CongratsPool) = stringPreferencesKey("congrats_bag_${pool.key}")
    }

    // Unreadable storage plays on with defaults; a failed write (full phone) is lost, not a crash
    private val data: Flow<Preferences> = context.dataStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }

    private suspend fun write(transform: suspend (MutablePreferences) -> Unit) {
        try {
            context.dataStore.edit(transform)
        } catch (_: IOException) {
        }
    }

    // Unchanged settings read GameSettings' defaults, so new defaults reach them
    val settingsFlow: Flow<GameSettings> = data.map { prefs ->
        val d = GameSettings()
        GameSettings(
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: d.soundEnabled,
            soundVolume = prefs[Keys.SOUND_VOLUME] ?: d.soundVolume,
            musicEnabled = prefs[Keys.MUSIC_ENABLED] ?: d.musicEnabled,
            musicVolume = prefs[Keys.MUSIC_VOLUME] ?: d.musicVolume,
            autoZoomEnabled = prefs[Keys.AUTO_ZOOM] ?: d.autoZoomEnabled,
            highlightFreeTiles = prefs[Keys.HIGHLIGHT_FREE_TILES] ?: d.highlightFreeTiles,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: d.vibrationEnabled,
            tableSurface = prefs[Keys.TABLE_SURFACE]
                ?.let(TableSurface::fromStoredName)
                ?: d.tableSurface,
            tileSet = prefs[Keys.TILE_SET]
                ?.let { name -> TileSet.entries.firstOrNull { it.name == name } }
                ?: d.tileSet,
            lampBrightness = prefs[Keys.LAMP_BRIGHTNESS] ?: d.lampBrightness,
            fireplaceLit = prefs[Keys.FIREPLACE] ?: d.fireplaceLit,
            lowPerformance = prefs[Keys.LOW_PERFORMANCE].takeIf { onThisPhone(prefs) } ?: d.lowPerformance,
            fireVolume = prefs[Keys.FIRE_VOLUME] ?: d.fireVolume,
            fireIntensity = prefs[Keys.FIRE_INTENSITY] ?: d.fireIntensity,
            wideBoards = prefs[Keys.WIDE_BOARDS]
                ?.let { name -> WideBoards.entries.firstOrNull { it.name == name } }
                ?: d.wideBoards,
        )
    }

    val progressFlow: Flow<Progress> = data.map { prefs ->
        Progress(
            highestLevelReached = prefs[Keys.HIGHEST_LEVEL] ?: 1,
            completedConstellations = prefs[Keys.COMPLETED_CONSTELLATIONS] ?: emptySet(),
            cleanConstellations = prefs[Keys.CLEAN_CONSTELLATIONS] ?: emptySet(),
            hints = prefs[Keys.HINTS] ?: 0,
            puzzleDaysSolved = prefs[Keys.PUZZLE_DAYS_SOLVED]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
            records = records(prefs),
        )
    }.distinctUntilChanged()

    /** Not yet run, it counts as [PerfCheck.MANUAL] once the player has set Low-performance mode. */
    val perfCheckFlow: Flow<PerfCheckState> = data.map { prefs ->
        if (!onThisPhone(prefs)) return@map PerfCheckState(PerfCheck.PENDING, 0)
        val stored = prefs[Keys.PERF_CHECK]?.let { name -> PerfCheck.entries.firstOrNull { it.name == name } }
            ?: if (prefs[Keys.LOW_PERFORMANCE] != null) PerfCheck.MANUAL else PerfCheck.PENDING
        val state = if (stored == PerfCheck.PASSED && (prefs[Keys.PERF_CHECK_VERSION] ?: 1) < PERF_CHECK_VERSION) PerfCheck.PENDING else stored
        PerfCheckState(state, prefs[Keys.PERF_CHECK_STUTTERS] ?: 0)
    }.distinctUntilChanged()

    // No phone named (before 1.4.12): this phone's
    private fun onThisPhone(prefs: Preferences) = prefs[Keys.PERF_DEVICE].let { it == null || it == thisPhone }

    // Drop what another phone left before writing
    private fun claimForThisPhone(prefs: MutablePreferences) {
        if (!onThisPhone(prefs)) {
            prefs.remove(Keys.PERF_CHECK)
            prefs.remove(Keys.PERF_CHECK_STUTTERS)
            prefs.remove(Keys.LOW_PERFORMANCE)
        }
        prefs[Keys.PERF_DEVICE] = thisPhone
    }

    /** Tests: as a backup from [phone] brings them (null: unnamed, pre-1.4.12). */
    internal suspend fun storedOnPhone(phone: String?) {
        write { prefs -> if (phone != null) prefs[Keys.PERF_DEVICE] = phone else prefs.remove(Keys.PERF_DEVICE) }
    }

    /** Tests: stored check version (null: pre-1.4.13). */
    internal suspend fun storedCheckVersion(version: Int?) {
        write { prefs -> if (version != null) prefs[Keys.PERF_CHECK_VERSION] = version else prefs.remove(Keys.PERF_CHECK_VERSION) }
    }

    /** Once per launch: unnamed (older) stored data is this phone's. */
    suspend fun namePerfPhone() {
        write { prefs -> if (prefs[Keys.PERF_DEVICE] == null) prefs[Keys.PERF_DEVICE] = thisPhone }
    }

    /** [stutterPercent]: share of frames that stuttered. */
    suspend fun setPerfCheck(state: PerfCheck, stutterPercent: Int? = null) {
        write { prefs ->
            claimForThisPhone(prefs)
            prefs[Keys.PERF_CHECK] = state.name
            prefs[Keys.PERF_CHECK_VERSION] = PERF_CHECK_VERSION
            if (stutterPercent != null) prefs[Keys.PERF_CHECK_STUTTERS] = stutterPercent
        }
    }

    /** Test builds: back to first-install state. */
    suspend fun resetPerfCheck() {
        write { prefs ->
            prefs.remove(Keys.PERF_CHECK)
            prefs.remove(Keys.PERF_CHECK_STUTTERS)
            prefs.remove(Keys.LOW_PERFORMANCE)
            prefs[Keys.PERF_DEVICE] = thisPhone
        }
    }

    /**
     * Writes only the settings that differ, so the rest keep following their defaults and a change
     * made meanwhile isn't overwritten.
     */
    suspend fun updateSettings(from: GameSettings, to: GameSettings) {
        if (from == to) return
        write { prefs ->
            fun <T> put(key: Preferences.Key<T>, value: (GameSettings) -> T) {
                if (value(to) != value(from)) prefs[key] = value(to)
            }
            put(Keys.SOUND_ENABLED) { it.soundEnabled }
            put(Keys.SOUND_VOLUME) { it.soundVolume }
            put(Keys.MUSIC_ENABLED) { it.musicEnabled }
            put(Keys.MUSIC_VOLUME) { it.musicVolume }
            put(Keys.AUTO_ZOOM) { it.autoZoomEnabled }
            put(Keys.HIGHLIGHT_FREE_TILES) { it.highlightFreeTiles }
            put(Keys.VIBRATION) { it.vibrationEnabled }
            put(Keys.TABLE_SURFACE) { it.tableSurface.name }
            put(Keys.TILE_SET) { it.tileSet.name }
            put(Keys.LAMP_BRIGHTNESS) { it.lampBrightness }
            put(Keys.FIREPLACE) { it.fireplaceLit }
            if (to.lowPerformance != from.lowPerformance) claimForThisPhone(prefs)
            put(Keys.LOW_PERFORMANCE) { it.lowPerformance }
            put(Keys.FIRE_VOLUME) { it.fireVolume }
            put(Keys.FIRE_INTENSITY) { it.fireIntensity }
            put(Keys.WIDE_BOARDS) { it.wideBoards.name }
        }
    }

    /** Hints earned since 1.4.6: existing players start with 2, new ones none. Settled once. */
    suspend fun migrateHints() {
        write { prefs ->
            if (prefs[Keys.HINTS] != null) return@write
            val played = (prefs[Keys.HIGHEST_LEVEL] ?: 1) > 1 ||
                !prefs[Keys.COMPLETED_CONSTELLATIONS].isNullOrEmpty() ||
                !prefs[Keys.PUZZLE_DAYS_SOLVED].isNullOrEmpty()
            prefs[Keys.HINTS] = if (played) EXISTING_PLAYER_HINTS else 0
        }
    }

    /** False if there were none. */
    suspend fun spendHint(): Boolean {
        var spent = false
        write { prefs ->
            val hints = prefs[Keys.HINTS] ?: 0
            if (hints > 0) {
                prefs[Keys.HINTS] = hints - 1
                spent = true
            }
        }
        return spent
    }

    /** Once: carries the stored highest level to the new numbering so nothing playable gets locked. */
    suspend fun migrateLevelOrder() {
        write { prefs ->
            val order = prefs[Keys.LEVEL_ORDER] ?: 1
            if (order >= JOURNEY_100_LEVEL_ORDER) return@write
            prefs[Keys.LEVEL_ORDER] = JOURNEY_100_LEVEL_ORDER
            val highest = prefs[Keys.HIGHEST_LEVEL] ?: return@write
            // Straight from the stored numbering to today's
            val before = when {
                order >= WIDE_MAH_LEVEL_ORDER -> LayoutDifficulty.levelsBeforeJourney100
                order >= MEASURED_LEVEL_ORDER -> LayoutDifficulty.levelsBeforeWideMah
                else -> LayoutDifficulty.levelsBeforeMeasuredOrder
            }
            prefs[Keys.HIGHEST_LEVEL] = LayoutDifficulty.highestAfterReorder(highest.coerceAtLeast(0), { ConstellationRepository.levelIndexOf(it) }, before)
        }
    }

    /** [day] is an epoch day; ticked on the calendar. */
    suspend fun recordPuzzleSolved(day: Long) {
        write { prefs ->
            prefs[Keys.PUZZLE_DAYS_SOLVED] = (prefs[Keys.PUZZLE_DAYS_SOLVED] ?: emptySet()) + day.toString()
        }
    }

    /** Best clears by layout id. */
    private fun records(prefs: Preferences): Map<String, LevelRecord> =
        prefs[Keys.LEVEL_RECORDS].orEmpty().mapNotNull { entry ->
            val at = entry.lastIndexOf('=')
            if (at <= 0) return@mapNotNull null
            LevelRecord.decode(entry.substring(at + 1))?.let { entry.substring(0, at) to it }
        }.toMap()

    /** Records a win; true on the first gold ribbon. */
    suspend fun recordGameWon(
        constellationId: String, levelIndex: Int?, clean: Boolean = false, puzzleDay: Long? = null, record: LevelRecord? = null,
    ): Boolean {
        var firstRibbon = false
        write { prefs ->
            // Keep the best clear
            if (record != null) {
                val best = records(prefs)[constellationId]
                if (best == null || record.beats(best)) {
                    prefs[Keys.LEVEL_RECORDS] = prefs[Keys.LEVEL_RECORDS].orEmpty()
                        .filterNot { it.substringBeforeLast('=') == constellationId }.toSet() + "$constellationId=${record.encode()}"
                }
            }
            if (clean) {
                val cleaned = prefs[Keys.CLEAN_CONSTELLATIONS] ?: emptySet()
                // Today's Puzzle is cleaned per day (no layout ribbon), else per layout; each first clean clear
                // earns a hint
                val key = puzzleDay?.let { "$PUZZLE_DAY_PREFIX$it" } ?: constellationId
                firstRibbon = cleaned.none { !it.startsWith(PUZZLE_DAY_PREFIX) } && puzzleDay == null
                if (key !in cleaned) {
                    prefs[Keys.CLEAN_CONSTELLATIONS] = cleaned + key
                    prefs[Keys.HINTS] = ((prefs[Keys.HINTS] ?: 0) + 1).coerceAtMost(MAX_HINTS)
                }
            }
            if (levelIndex != null) {
                val currentHighest = prefs[Keys.HIGHEST_LEVEL] ?: 1
                if (levelIndex >= currentHighest) {
                    prefs[Keys.HIGHEST_LEVEL] = levelIndex + 1
                }
            }

            val completedSet = (prefs[Keys.COMPLETED_CONSTELLATIONS] ?: emptySet()).toMutableSet()
            completedSet.add(constellationId)
            prefs[Keys.COMPLETED_CONSTELLATIONS] = completedSet
        }
        return firstRibbon
    }

    /**
     * Context pools are used some of the time; each pool is a shuffled deck persisted across launches,
     * so nothing repeats until the whole pool has been seen.
     */
    suspend fun nextCongratsMessage(isTutorial: Boolean, now: LocalDateTime = LocalDateTime.now()): String {
        var message = CongratsPool.GENERAL.messages.first()
        write { prefs ->
            // Day starts at 4 am, so just after midnight counts as the night before
            val today = now.minusHours(4).toLocalDate().toEpochDay()
            val firstToday = (prefs[Keys.LAST_WIN_DAY] ?: Long.MIN_VALUE) != today
            prefs[Keys.LAST_WIN_DAY] = today
            val hour = now.hour
            val pool = when {
                isTutorial -> CongratsPool.TUTORIAL
                firstToday && Random.nextFloat() < 0.5f -> CongratsPool.FIRST_TODAY
                (hour >= 22 || hour < 4) && Random.nextFloat() < 0.4f -> CongratsPool.LATE_NIGHT
                hour in 5..10 && Random.nextFloat() < 0.3f -> CongratsPool.MORNING
                else -> CongratsPool.GENERAL
            }
            message = pool.messages[dealFromBag(prefs, pool)]
        }
        return message
    }

    // "seed,position,lastOfPreviousBag": order rebuilt from the seed; a new bag never starts with the
    // previous bag's last
    private fun dealFromBag(prefs: MutablePreferences, pool: CongratsPool): Int {
        val size = pool.messages.size
        val parts = prefs[Keys.congratsBag(pool)]?.split(",")?.mapNotNull { it.toLongOrNull() }
        var seed = parts?.getOrNull(0) ?: 0L
        var position = parts?.getOrNull(1)?.toInt()?.coerceAtLeast(0) ?: size
        var previousLast = parts?.getOrNull(2)?.toInt() ?: -1
        fun order(): List<Int> {
            val o = (0 until size).shuffled(Random(seed)).toMutableList()
            if (size > 1 && o[0] == previousLast) o[0] = o[1].also { o[1] = o[0] }
            return o
        }
        if (position >= size) {
            if (parts != null) previousLast = order().last()
            seed = Random.nextLong()
            position = 0
        }
        val index = order()[position]
        prefs[Keys.congratsBag(pool)] = "$seed,${position + 1},$previousLast"
        return index
    }

    // Re-parsed only when the save changed
    val savedGameFlow: Flow<SavedGameState?> = data.map { prefs -> prefs[Keys.SAVED_GAME] ?: "" }
        .distinctUntilChanged()
        .map { raw -> if (raw.isNotEmpty()) SavedGameState.deserialize(raw) else null }
        // Read back after every match, so parsed off the main thread
        .flowOn(Dispatchers.Default)

    suspend fun saveGame(savedState: SavedGameState) {
        write { prefs ->
            prefs[Keys.SAVED_GAME] = savedState.serialize()
        }
    }

    suspend fun clearSavedGame() {
        write { prefs ->
            prefs.remove(Keys.SAVED_GAME)
        }
    }
}

private const val EXISTING_PLAYER_HINTS = 2

// Today's Puzzle clean-clear keys, in the same set as layout ids
private const val PUZZLE_DAY_PREFIX = "day:"
