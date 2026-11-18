package com.vortessence.mahjong.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vortessence.mahjong.R
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.TileSet
import com.vortessence.mahjong.ui.components.ImperialImages
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.random.Random

class AudioManager(private val context: Context) {

    private val musicPlayer = BackgroundMusicPlayer(context.applicationContext)
    private val fireplace = FireplaceSound(context.applicationContext)
    private var currentSettings: GameSettings? = null
    private var isInForeground = false
    private var room = FireRoom.NONE
    private var matchStreak = 0

    // Focus held only while music plays, so calls and other apps pause music and fire; with music
    // off, fire and tile sounds play alongside other audio
    private val system = context.getSystemService(android.media.AudioManager::class.java)
    private var focusHeld = false
    // Asked for again only when the player next comes back
    private var focusLost = false
    private val focusRequest = AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener({ onFocusChange(it) }, Handler(Looper.getMainLooper()))
        .build()
    private var lastMatchTimeMs = 0L

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    // Built by tools/make-tap-sounds.py
    private val pickSoundIds = intArrayOf(
        soundPool.load(context.applicationContext, R.raw.tile_tap_1, 1),
        soundPool.load(context.applicationContext, R.raw.tile_tap_2, 1),
        soundPool.load(context.applicationContext, R.raw.tile_tap_3, 1),
        soundPool.load(context.applicationContext, R.raw.tile_tap_4, 1),
    )

    // Four takes, plus a random 3% speed per match, so repeats never sound mechanical. Built by
    // tools/make-match-sounds.py
    private val matchSoundIds = intArrayOf(
        soundPool.load(context.applicationContext, R.raw.tile_match_1, 1),
        soundPool.load(context.applicationContext, R.raw.tile_match_2, 1),
        soundPool.load(context.applicationContext, R.raw.tile_match_3, 1),
        soundPool.load(context.applicationContext, R.raw.tile_match_4, 1),
    )

    // Imperial's stone sounds (the same tools with --stone): loaded only once Imperial is chosen,
    // since most players never have it and open-source builds never can
    private var stonePickSoundIds: IntArray? = null
    private var stoneMatchSoundIds: IntArray? = null

    private fun loadStoneSounds() {
        if (stonePickSoundIds != null) return
        val app = context.applicationContext
        stonePickSoundIds = intArrayOf(
            soundPool.load(app, R.raw.stone_tap_1, 1),
            soundPool.load(app, R.raw.stone_tap_2, 1),
        )
        stoneMatchSoundIds = intArrayOf(
            soundPool.load(app, R.raw.stone_match_1, 1),
            soundPool.load(app, R.raw.stone_match_2, 1),
            soundPool.load(app, R.raw.stone_match_3, 1),
            soundPool.load(app, R.raw.stone_match_4, 1),
        )
    }

    // Only where the build has the Imperial images; the usual sounds until the stone ones are loaded
    private val stone get() = currentSettings?.tileSet == TileSet.IMPERIAL && ImperialImages.available
    private val pickSounds get() = (if (stone) stonePickSoundIds else null) ?: pickSoundIds
    private val matchSounds get() = (if (stone) stoneMatchSoundIds else null) ?: matchSoundIds

    // Built by tools/make-knock-sound.py
    private val blockedSoundIds = intArrayOf(soundPool.load(context.applicationContext, R.raw.tile_blocked, 1))

    // While the Fire slider is dragged, heard at table volume (it's set by ear for the table)
    private var auditioningFire = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun updateSettings(settings: GameSettings) {
        val previousSettings = currentSettings
        currentSettings = settings
        syncFocus()
        if (stone) loadStoneSounds()

        if (musicOn(settings)) {
            musicPlayer.setVolume(loudness(settings.musicVolume))
            val wasMusicEnabled = previousSettings?.let { musicOn(it) } ?: false
            if (!canPlayMusic) {
                // Never play in the background or without focus; onResume() or regaining focus
                // starts it. Stopping makes it pick a random song when just enabled.
                if (!wasMusicEnabled) musicPlayer.stop()
            } else if (!wasMusicEnabled) {
                musicPlayer.startRandom()
            } else {
                musicPlayer.resume()
            }
        } else {
            // So re-enabling starts with a random song
            musicPlayer.stop()
        }
        if (isInForeground) fireplace.play(fireLevel(settings))
    }

    private fun musicOn(settings: GameSettings) = settings.musicEnabled && settings.musicVolume > 0f

    private val canPlayMusic get() = isInForeground && focusHeld && !focusLost

    private fun syncFocus() {
        val want = isInForeground && currentSettings?.let { musicOn(it) } == true
        if (want && !focusHeld && !focusLost) {
            val granted = system?.requestAudioFocus(focusRequest) ?: android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusHeld = granted == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            // Refused (in a call, say): silent until the player comes back
            if (!focusHeld) {
                focusLost = true
                fireplace.pause()
            }
        } else if (!want && focusHeld) {
            system?.abandonAudioFocusRequest(focusRequest)
            focusHeld = false
        }
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                focusLost = false
                if (isInForeground) resumeAmbience()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusLost = true
                pauseAmbience()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                // Asked again when the player comes back
                focusLost = true
                pauseAmbience()
                system?.abandonAudioFocusRequest(focusRequest)
                focusHeld = false
            }
            // Ducking: Android turns it down itself
            else -> Unit
        }
    }

    private fun resumeAmbience() {
        val settings = currentSettings ?: return
        if (musicOn(settings) && canPlayMusic) musicPlayer.resume()
        fireplace.play(fireLevel(settings))
        fireplace.resume()
    }

    private fun pauseAmbience() {
        musicPlayer.pause()
        fireplace.pause()
        releaseWinPlayer()
    }

    /** Whether the Fire slider is being dragged. */
    fun auditionFire(on: Boolean) {
        auditioningFire = on
        val settings = currentSettings ?: return
        if (isInForeground) fireplace.play(fireLevel(settings))
    }

    /** Fire at its set volume at the table, a fifth on menu screens, silent on the title screen. */
    fun setFireRoom(room: FireRoom) {
        this.room = room
        // Win fireworks are only kept ready at the table
        if (room == FireRoom.TABLE && isInForeground) prepareWin() else dropReadyWin()
        val settings = currentSettings ?: return
        if (isInForeground) fireplace.play(fireLevel(settings))
    }

    /** Up to just under half the recording's level, louder as the fire burns brighter; menus always crackle. */
    private fun fireLevel(settings: GameSettings): Float {
        val level = loudness(settings.fireVolume) * 0.45f * (0.8f + 0.4f * settings.fireIntensity)
        if (auditioningFire) return level
        // Fire is out while the phone's Remove animations is on
        if (!settings.animationsEnabled) return 0f
        return when (room) {
            FireRoom.TABLE -> if (settings.fireplaceLit) level else 0f
            FireRoom.MENU -> level * MENU_FIRE_SHARE
            FireRoom.NONE -> 0f
        }
    }

    fun onResume() {
        isInForeground = true
        focusLost = false
        if (room == FireRoom.TABLE) prepareWin()
        syncFocus()
        // No music until settings are loaded
        if (!focusLost) resumeAmbience()
    }

    fun onPause() {
        isInForeground = false
        syncFocus()
        musicPlayer.pause()
        fireplace.pause()
        releaseWinPlayer()
        dropReadyWin()
        pendingWinVolume = null
    }

    fun release() {
        // So nothing more is made ready
        isInForeground = false
        syncFocus()
        releaseWinPlayer()
        dropReadyWin()
        winLoader.shutdown()
        musicPlayer.release()
        fireplace.stop()
        try {
            soundPool.release()
        } catch (_: Exception) {
        }
    }

    fun playTileSelect() {
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playRandomSound(pickSounds, loudness(settings.soundVolume))
        }
        vibrate { HapticPatterns.select(it) }
    }

    // Title-screen menu: a quiet, higher tile-pick tick
    fun playMenuClick() {
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playRandomSound(pickSoundIds, loudness(settings.soundVolume) * 0.175f, rate = 1.25f)
        }
        vibrate { HapticPatterns.select(it) }
    }

    fun playTileDeselect() {
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playRandomSound(pickSounds, loudness(settings.soundVolume))
        }
        vibrate { HapticPatterns.deselect(it) }
    }

    fun playTileMatch() {
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playRandomSound(matchSounds, loudness(settings.soundVolume), rate = 0.97f + Random.nextFloat() * 0.06f)
        }
        val now = SystemClock.elapsedRealtime()
        matchStreak = if ((now - lastMatchTimeMs) <= MATCH_STREAK_WINDOW_MS) {
            (matchStreak + 1).coerceAtMost(HapticPatterns.MAX_MATCH_STREAK)
        } else {
            0
        }
        lastMatchTimeMs = now
        vibrate { HapticPatterns.match(it, matchStreak) }
    }

    fun playTileBlocked() {
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playRandomSound(blockedSoundIds, loudness(settings.soundVolume))
        }
        vibrate { HapticPatterns.blocked() }
    }

    fun playWin() {
        if (!isInForeground || focusLost) return
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playWinPhrase(loudness(settings.soundVolume))
        }
        vibrate { HapticPatterns.win(it) }
    }

    // Win fireworks, as long as the light show (5.5 s, tools/make-win-sound.py): too long for the
    // SoundPool, so its own player, with the music ducked beneath it
    private var winPlayer: MediaPlayer? = null
    private var winBoost: LoudnessEnhancer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Prepared off the main thread and kept ready while a board is open, so a win starts without a
    // hitch. Otherwise main thread only
    private class WinSound(val player: MediaPlayer, val boost: LoudnessEnhancer?)
    private val winLoader = Executors.newSingleThreadExecutor { r -> Thread(r, "win-sound").apply { isDaemon = true } }
    private var readyWin: WinSound? = null
    private var winLoading = false
    // Volume of a win that came before the player was ready
    private var pendingWinVolume: Float? = null

    private fun prepareWin() {
        if (readyWin != null || winLoading) return
        winLoading = true
        if (winLoader.isShutdown) return
        winLoader.execute {
            val made = runCatching { makeWinSound() }.getOrNull()
            mainHandler.post {
                winLoading = false
                if (made == null) return@post
                val volume = pendingWinVolume
                pendingWinVolume = null
                when {
                    !isInForeground -> release(made)
                    volume != null -> startWin(made, volume)
                    room == FireRoom.TABLE && readyWin == null -> readyWin = made
                    else -> release(made)
                }
            }
        }
    }

    private fun makeWinSound(): WinSound? {
        val player = MediaPlayer.create(
            context.applicationContext,
            R.raw.win_fireworks,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            // Own session for the enhancer: on Android 13 AUDIO_SESSION_ID_GENERATE leaves it on
            // session 0 (the whole output), which the enhancer can't use
            context.getSystemService(android.media.AudioManager::class.java)?.generateAudioSessionId()
                ?: android.media.AudioManager.AUDIO_SESSION_ID_GENERATE,
        ) ?: return null
        // Already peaks at full scale, so boosted by the enhancer, which keeps peaks from distorting
        val boost = runCatching {
            LoudnessEnhancer(player.audioSessionId).apply {
                setTargetGain(WIN_BOOST_MILLIBELS)
                enabled = true
            }
        }.getOrNull()
        return WinSound(player, boost)
    }

    private fun playWinPhrase(volume: Float) {
        releaseWinPlayer()
        val ready = readyWin
        readyWin = null
        if (ready != null) {
            startWin(ready, volume)
        } else {
            // Not ready yet (board just opened): starts as soon as it is
            pendingWinVolume = volume
            prepareWin()
        }
    }

    private fun startWin(sound: WinSound, volume: Float) {
        val level = volume.coerceIn(0f, 1f)
        sound.player.setVolume(level, level)
        sound.player.setOnCompletionListener {
            if (winPlayer === it) releaseWinPlayer()
        }
        winPlayer = sound.player
        winBoost = sound.boost
        rampMusic(to = 0.4f)
        sound.player.start()
    }

    private fun release(sound: WinSound) {
        runCatching { sound.boost?.release() }
        runCatching { sound.player.release() }
    }

    private fun dropReadyWin() {
        readyWin?.let(::release)
        readyWin = null
    }

    private fun releaseWinPlayer() {
        val player = winPlayer ?: return
        winPlayer = null
        runCatching { winBoost?.release() }
        winBoost = null
        try {
            player.release()
        } catch (_: Exception) {
        }
        rampMusic(to = 1f)
        if (room == FireRoom.TABLE && isInForeground) prepareWin()
    }

    // Over about half a second
    private fun rampMusic(to: Float) {
        mainHandler.removeCallbacksAndMessages(MUSIC_RAMP)
        val from = musicDuck
        val steps = 12
        for (i in 1..steps) {
            mainHandler.postAtTime(
                {
                    musicDuck = from + (to - from) * i / steps
                    musicPlayer.duck(musicDuck)
                },
                MUSIC_RAMP,
                SystemClock.uptimeMillis() + i * 40L,
            )
        }
    }

    private var musicDuck = 1f

    private fun playRandomSound(soundIds: IntArray, volume: Float, rate: Float = 1.0f) {
        if ((volume <= 0f) || soundIds.isEmpty()) return
        val soundId = soundIds[Random.nextInt(soundIds.size)]
        val scaledVolume = volume.coerceIn(0f, 1f)
        try {
            soundPool.play(soundId, scaledVolume, scaledVolume, 1, 0, rate)
        } catch (_: Exception) {
        }
    }

    private fun vibrate(effect: (Vibrator) -> VibrationEffect) {
        if (currentSettings?.vibrationEnabled != true) return
        val vibrator = vibrator ?: return
        try {
            vibrator.vibrate(effect(vibrator))
        } catch (_: Exception) {
            // No vibrator, or no permission
        }
    }

    private companion object {
        // Matches within this window build the haptic streak
        const val MATCH_STREAK_WINDOW_MS = 4000L

        // Token so a new ramp can cancel one in progress
        private val MUSIC_RAMP = Any()

        // 500 mB = 5 dB
        const val WIN_BOOST_MILLIBELS = 500
    }
}

/** Slider 0..1 to gain on a 40 dB scale, so loudness changes evenly. 0 is silent. */
internal fun loudness(slider: Float): Float =
    if (slider <= 0f) 0f else 10f.pow(2f * (slider.coerceAtMost(1f) - 1f))

enum class FireRoom { TABLE, MENU, NONE }

private const val MENU_FIRE_SHARE = 0.2f
