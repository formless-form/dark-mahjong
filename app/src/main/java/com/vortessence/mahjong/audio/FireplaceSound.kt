package com.vortessence.mahjong.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.vortessence.mahjong.R

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
    private val lock = Any()

    /** Plays at [level] (0..1), or stops when it is 0. */
    fun play(level: Float) {
        synchronized(lock) {
            volume = level.coerceIn(0f, 1f)
            if (volume <= 0f) {
                stop()
                return
            }
            players.forEach { runCatching { it?.setVolume(volume, volume) } }
            if (playing) return
            playing = true
            try {
                val first = create() ?: return
                val second = create() ?: return
                players[0] = first
                players[1] = second
                current = 0
                first.setNextMediaPlayer(second)
                first.start()
            } catch (_: Exception) {
                stop()
            }
        }
    }

    fun pause() {
        synchronized(lock) {
            runCatching { players[current]?.takeIf { it.isPlaying }?.pause() }
        }
    }

    fun resume() {
        synchronized(lock) {
            if (playing && volume > 0f) runCatching { players[current]?.start() }
        }
    }

    fun stop() {
        synchronized(lock) {
            playing = false
            players.forEachIndexed { i, p ->
                runCatching { p?.release() }
                players[i] = null
            }
        }
    }

    private fun create(): MediaPlayer? {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val player = MediaPlayer.create(context, R.raw.fireplace, attributes, 0) ?: return null
        player.setVolume(volume, volume)
        // When one finishes, the other has already taken over: re-arm the finished one to
        // follow it, so they keep handing over for ever
        player.setOnCompletionListener { done ->
            synchronized(lock) {
                if (!playing) return@synchronized
                val next = 1 - players.indexOf(done)
                current = next
                runCatching {
                    done.release()
                    val fresh = create() ?: return@runCatching
                    players[1 - next] = fresh
                    players[next]?.setNextMediaPlayer(fresh)
                }
            }
        }
        return player
    }
}
