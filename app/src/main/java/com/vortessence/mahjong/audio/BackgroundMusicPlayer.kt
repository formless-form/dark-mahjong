package com.vortessence.mahjong.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.vortessence.mahjong.R
import kotlin.random.Random

class BackgroundMusicPlayer(private val context: Context) {

    private val playlist = intArrayOf(
        R.raw.zen_mountain_mist,
        R.raw.zen_mountain_stream,
        R.raw.ink_cloud_meditation,
        R.raw.ink_cloud_study,
        R.raw.jade_rain_study,
    )

    private var mediaPlayer: MediaPlayer? = null
    private var currentIndex = -1
    private var currentVolume = 0.4f
    // Temporary lowering under other sounds (the win phrase); 1 means none
    private var duckFactor = 1f
    private var isStarted = false
    private var isPaused = false
    private val lock = Any()

    // The music's volume setting runs up to this much of the tracks' own level: they are mastered
    // loud, and at full level they drowned out the game
    private val maxLevel = 0.44f

    private fun level() = currentVolume * maxLevel * duckFactor

    fun setVolume(volume: Float) {
        synchronized(lock) {
            currentVolume = volume.coerceIn(0f, 1f)
            try {
                mediaPlayer?.setVolume(level(), level())
            } catch (_: Exception) {
                // Ignore if media player is not in a valid state
            }
        }
    }

    /** Plays the music at [factor] of its volume (1 restores it), e.g. under the win phrase. */
    fun duck(factor: Float) {
        synchronized(lock) {
            duckFactor = factor.coerceIn(0f, 1f)
            try {
                mediaPlayer?.setVolume(level(), level())
            } catch (_: Exception) {
                // Ignore if media player is not in a valid state
            }
        }
    }

    fun startRandom() {
        synchronized(lock) {
            if (playlist.isEmpty()) return
            currentIndex = Random.nextInt(playlist.size)
            isStarted = true
            isPaused = false
            playTrack(currentIndex)
        }
    }

    private fun playNextTrack() {
        synchronized(lock) {
            if (!isStarted || isPaused || playlist.isEmpty()) return
            currentIndex = (currentIndex + 1) % playlist.size
            playTrack(currentIndex)
        }
    }

    private fun playTrack(index: Int) {
        releasePlayer()

        if (index !in playlist.indices) return
        val resId = playlist[index]

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val player = MediaPlayer.create(context, resId, audioAttributes, 0)
                ?: MediaPlayer.create(context, resId)
                ?: return

            player.setVolume(level(), level())
            player.setOnCompletionListener {
                playNextTrack()
            }
            player.start()
            mediaPlayer = player
        } catch (_: Exception) {
            // Ignore playback interruption
        }
    }

    fun pause() {
        synchronized(lock) {
            isPaused = true
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                }
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    fun resume() {
        synchronized(lock) {
            if (!isStarted) {
                startRandom()
                return
            }
            isPaused = false
            try {
                if (mediaPlayer != null) {
                    mediaPlayer?.start()
                } else if (currentIndex in playlist.indices) {
                    playTrack(currentIndex)
                } else {
                    startRandom()
                }
            } catch (_: Exception) {
                startRandom()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            isStarted = false
            isPaused = false
            releasePlayer()
        }
    }

    fun release() {
        stop()
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (_: Exception) {
            // Ignore
        }
        mediaPlayer = null
    }
}
