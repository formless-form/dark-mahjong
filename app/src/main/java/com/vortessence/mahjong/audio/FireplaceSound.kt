package com.vortessence.mahjong.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.vortessence.mahjong.R
import java.util.concurrent.Executors

/**
 * Looping fire crackle. The recording's ends are crossfaded (tools/make-fire-sound.py) and two
 * players hand over via setNextMediaPlayer, so the loop has neither gap nor jump.
 */
class FireplaceSound(private val context: Context) {

    private val players = arrayOfNulls<MediaPlayer>(2)
    private var current = 0
    private var volume = 0f
    private var playing = false
    private var paused = false
    // Counts stops, so players made for a since-stopped play are let go, not started
    private var generation = 0
    private val lock = Any()

    // Players are made (decoding the start) off the main thread and outside the lock, so neither
    // opening a menu nor pausing waits on them
    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "fireplace").apply { isDaemon = true } }

    /** [level] 0..1; 0 stops. */
    fun play(level: Float) {
        val gen: Int
        synchronized(lock) {
            volume = level.coerceIn(0f, 1f)
            if (volume <= 0f) {
                stop()
                return
            }
            players.forEach { runCatching { it?.setVolume(volume, volume) } }
            if (playing) return
            playing = true
            gen = generation
        }
        loader.execute { begin(gen) }
    }

    // On the loader; starts only if this play is still wanted
    private fun begin(gen: Int) {
        val first = create()
        val second = first?.let { create() }
        synchronized(lock) {
            val wanted = playing && gen == generation && players[0] == null && players[1] == null
            if (first == null || second == null || !wanted) {
                runCatching { first?.release() }
                runCatching { second?.release() }
                // So the next play() tries again
                if (wanted) playing = false
                return
            }
            try {
                players[0] = first
                players[1] = second
                current = 0
                // Volume may have changed meanwhile
                first.setVolume(volume, volume)
                second.setVolume(volume, volume)
                first.setNextMediaPlayer(second)
                // Else resume() starts it
                if (!paused) first.start()
            } catch (_: Exception) {
                stop()
            }
        }
    }

    fun pause() {
        synchronized(lock) {
            paused = true
            runCatching { players[current]?.takeIf { it.isPlaying }?.pause() }
        }
    }

    fun resume() {
        synchronized(lock) {
            paused = false
            if (playing && volume > 0f) runCatching { players[current]?.start() }
        }
    }

    fun stop() {
        synchronized(lock) {
            playing = false
            generation++
            players.forEachIndexed { i, p ->
                runCatching { p?.release() }
                players[i] = null
            }
        }
    }

    // On the main thread (the loader has no looper); the other player has already taken over
    private fun onRoundEnded(done: MediaPlayer) {
        val gen: Int
        val slot: Int
        synchronized(lock) {
            slot = players.indexOf(done)
            if (!playing || slot < 0) return
            current = 1 - slot
            players[slot] = null
            runCatching { done.release() }
            gen = generation
        }
        loader.execute { rearm(gen, slot) }
    }

    // On the loader: a fresh player for [slot] to follow the one now playing
    private fun rearm(gen: Int, slot: Int) {
        val fresh = create()
        synchronized(lock) {
            if (!playing || gen != generation) {
                runCatching { fresh?.release() }
                return
            }
            // Couldn't be made: the loop ends after this round (the next rearm finds nothing playing)
            if (fresh == null || players[slot] != null) {
                runCatching { fresh?.release() }
                return
            }
            val other = players[1 - slot]
            if (other == null) {
                // Nothing to follow: the next play() starts afresh
                runCatching { fresh.release() }
                stop()
                return
            }
            try {
                fresh.setVolume(volume, volume)
                players[slot] = fresh
                other.setNextMediaPlayer(fresh)
            } catch (_: Exception) {
                stop()
            }
        }
    }

    private fun create(): MediaPlayer? {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val player = runCatching { MediaPlayer.create(context, R.raw.fireplace, attributes, 0) }.getOrNull() ?: return null
        player.setOnCompletionListener { onRoundEnded(it) }
        return player
    }
}
