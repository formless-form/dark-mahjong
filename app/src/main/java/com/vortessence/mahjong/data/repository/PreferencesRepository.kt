package com.vortessence.mahjong.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.model.SavedGameState
import com.vortessence.mahjong.data.model.TableSurface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import kotlin.random.Random

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mahjong_preferences")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val AUTO_ZOOM = booleanPreferencesKey("auto_zoom")
        val HIGHLIGHT_FREE_TILES = booleanPreferencesKey("highlight_free_tiles")
        val ANIMATIONS = booleanPreferencesKey("animations")
        val VIBRATION = booleanPreferencesKey("vibration")
        val THEME = stringPreferencesKey("theme")

        val HIGHEST_LEVEL = intPreferencesKey("highest_level")
        val COMPLETED_CONSTELLATIONS = stringSetPreferencesKey("completed_constellations")
        val SAVED_GAME = stringPreferencesKey("saved_game")
        val TABLE_SURFACE = stringPreferencesKey("table_surface")
        val LAMP_BRIGHTNESS = floatPreferencesKey("lamp_brightness")
        val FIREPLACE = booleanPreferencesKey("fireplace")
        val FIRE_VOLUME = floatPreferencesKey("fire_volume")
        val FIRE_INTENSITY = floatPreferencesKey("fire_intensity")
        val LAST_WIN_DAY = longPreferencesKey("last_win_epoch_day")
        fun congratsBag(pool: CongratsPool) = stringPreferencesKey("congrats_bag_${pool.key}")
    }

    val settingsFlow: Flow<GameSettings> = context.dataStore.data.map { prefs ->
        val surfaceStr = prefs[Keys.TABLE_SURFACE] ?: prefs[Keys.THEME] ?: TableSurface.GREEN_MAT.name
        val surface = when (surfaceStr) {
            "WOOD" -> TableSurface.WALNUT
            "DARK" -> TableSurface.GREEN_MAT
            else -> try {
                TableSurface.valueOf(surfaceStr)
            } catch (_: Exception) {
                TableSurface.GREEN_MAT
            }
        }

        GameSettings(
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            soundVolume = prefs[Keys.SOUND_VOLUME] ?: 0.7f,
            musicEnabled = prefs[Keys.MUSIC_ENABLED] ?: true,
            musicVolume = prefs[Keys.MUSIC_VOLUME] ?: 0.6f,
            autoZoomEnabled = prefs[Keys.AUTO_ZOOM] ?: true,
            highlightFreeTiles = prefs[Keys.HIGHLIGHT_FREE_TILES] ?: false,
            animationsEnabled = prefs[Keys.ANIMATIONS] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            tableSurface = surface,
            lampBrightness = prefs[Keys.LAMP_BRIGHTNESS] ?: 0.5f,
            fireplaceLit = prefs[Keys.FIREPLACE] ?: false,
            fireVolume = prefs[Keys.FIRE_VOLUME] ?: 0.3f,
            fireIntensity = prefs[Keys.FIRE_INTENSITY] ?: 0.5f,
        )
    }

    val progressFlow: Flow<Progress> = context.dataStore.data.map { prefs ->
        Progress(
            highestLevelReached = prefs[Keys.HIGHEST_LEVEL] ?: 1,
            completedConstellations = prefs[Keys.COMPLETED_CONSTELLATIONS] ?: emptySet(),
        )
    }

    suspend fun updateSettings(settings: GameSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SOUND_ENABLED] = settings.soundEnabled
            prefs[Keys.SOUND_VOLUME] = settings.soundVolume
            prefs[Keys.MUSIC_ENABLED] = settings.musicEnabled
            prefs[Keys.MUSIC_VOLUME] = settings.musicVolume
            prefs[Keys.AUTO_ZOOM] = settings.autoZoomEnabled
            prefs[Keys.HIGHLIGHT_FREE_TILES] = settings.highlightFreeTiles
            prefs[Keys.ANIMATIONS] = settings.animationsEnabled
            prefs[Keys.VIBRATION] = settings.vibrationEnabled
            prefs[Keys.TABLE_SURFACE] = settings.tableSurface.name
            prefs[Keys.THEME] = settings.tableSurface.name
            prefs[Keys.LAMP_BRIGHTNESS] = settings.lampBrightness
            prefs[Keys.FIREPLACE] = settings.fireplaceLit
            prefs[Keys.FIRE_VOLUME] = settings.fireVolume
            prefs[Keys.FIRE_INTENSITY] = settings.fireIntensity
        }
    }

    suspend fun recordGameWon(constellationId: String, levelIndex: Int?) {
        context.dataStore.edit { prefs ->
            // Level progression
            if (levelIndex != null) {
                val currentHighest = prefs[Keys.HIGHEST_LEVEL] ?: 1
                if (levelIndex >= currentHighest) {
                    prefs[Keys.HIGHEST_LEVEL] = levelIndex + 1
                }
            }

            // Completed set
            val completedSet = (prefs[Keys.COMPLETED_CONSTELLATIONS] ?: emptySet()).toMutableSet()
            completedSet.add(constellationId)
            prefs[Keys.COMPLETED_CONSTELLATIONS] = completedSet
        }
    }

    /**
     * Picks the congratulations for a cleared board. Context pools (tutorial, first win of the day,
     * late night, morning) are used some of the time; every pool is dealt as a shuffled deck that
     * persists across launches, so no message repeats until its whole pool has been seen.
     */
    suspend fun nextCongratsMessage(isTutorial: Boolean, now: LocalDateTime = LocalDateTime.now()): String {
        var message = CongratsPool.GENERAL.messages.first()
        context.dataStore.edit { prefs ->
            val today = now.toLocalDate().toEpochDay()
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

    // Bag state "seed,position,lastOfPreviousBag": the order is rebuilt from the seed, and a new
    // bag never starts with the message the previous bag ended on
    private fun dealFromBag(prefs: MutablePreferences, pool: CongratsPool): Int {
        val size = pool.messages.size
        val parts = prefs[Keys.congratsBag(pool)]?.split(",")?.mapNotNull { it.toLongOrNull() }
        var seed = parts?.getOrNull(0) ?: 0L
        var position = parts?.getOrNull(1)?.toInt() ?: size
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

    val savedGameFlow: Flow<SavedGameState?> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.SAVED_GAME] ?: ""
        if (raw.isNotEmpty()) {
            SavedGameState.deserialize(raw)
        } else {
            null
        }
    }

    suspend fun saveGame(savedState: SavedGameState) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SAVED_GAME] = savedState.serialize()
        }
    }

    suspend fun clearSavedGame() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SAVED_GAME)
        }
    }
}
