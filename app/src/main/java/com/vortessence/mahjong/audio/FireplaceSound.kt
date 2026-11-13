package com.vortessence.mahjong.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.vortessence.mahjong.R
import java.util.concurrent.Executors

/**
 * The fireplace's soft crackle, looping for as long as the fire burns. The recording's ends are
 * crossfaded into each other (tools/make-fire-sound.py), and two players hand over to each other
 * with setNextMediaPlayer, so the loop point has neither a gap nor a jump.
 */
class FireplaceSound(private val context: Context) {

    private val players = arrayOfNulls<MediaPlayer>(2)
    private var current = 0
    private var volume = 0f
    private var playing = false
    private var paused = false
    // Counts stops, so players made for a play that has since stopped are let go, not started
    private var generation = 0
    private val lock = Any()

    // The players are made (each decodes the start of the recording) off the main thread, and
    // outside the lock, so neither opening a menu screen nor pausing ever waits on them
    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "fireplace").apply { isDaemon = true } }

    /** Plays at [level] (0..1), or stops when it is 0. */
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

    // On the loader: the pair made, then started if this play is still wanted
    private fun begin(gen: Int) {
        val first = create()
        val second = first?.let { create() }
        synchronized(lock) {
            val wanted = playing && gen == generation && players[0] == null && players[1] == null
            if (first == null || second == null || !wanted) {
                runCatching { first?.release() }
                runCatching { second?.release() }
                // Couldn't be made: no longer counted as playing, so the next play() tries again
                if (wanted) playing = false
                return
            }
            try {
                players[0] = first
                players[1] = second
                current = 0
                // The volume may have changed while they were made
                first.setVolume(volume, volume)
                second.setVolume(volume, volume)
                first.setNextMediaPlayer(second)
                // Made while the app was going to the background: resume() starts it
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

    // One round of the recording is over and the other player has taken over. Called on the main
    // thread (the players are made on the loader, which has no looper of its own)
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
        // The next round's player is made on the loader too, while the other plays its round
        loader.execute { rearm(gen, slot) }
    }

    // On the loader: a fresh player for [slot], set to follow the one now playing
    private fun rearm(gen: Int, slot: Int) {
        val fresh = create()
        synchronized(lock) {
            if (!playing || gen != generation) {
                runCatching { fresh?.release() }
                return
            }
            // Couldn't be made: the round now playing plays out, and the loop ends with it (the
            // next round's rearm finds nothing playing)
            if (fresh == null || players[slot] != null) {
                runCatching { fresh?.release() }
                return
            }
            val other = players[1 - slot]
            if (other == null) {
                // Nothing left playing to follow: no longer counted as playing, so the next
                // play() starts the loop afresh
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
        // When one finishes, the other has already taken over: the finished one is replaced by a
        // fresh player that follows it, so they keep handing over for ever
        player.setOnCompletionListener { onRoundEnded(it) }
        return player
    }
}
