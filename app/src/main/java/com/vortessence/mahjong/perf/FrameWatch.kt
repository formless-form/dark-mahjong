package com.vortessence.mahjong.perf

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import com.vortessence.mahjong.BuildConfig
import com.vortessence.mahjong.ui.components.TileRenderer

/**
 * Times the frames of the player's first game, once, for the first-game performance check: while
 * [setRecording] is on, each frame's drawing time goes into a [FrameTally] that lasts as long as
 * the app (a recreated activity carries on where the last left off). The phone's own frame
 * timings are read on a thread of their own; the verdict comes back on the main thread, once.
 */
class FrameWatch(private val window: Window, private val onVerdict: (FrameVerdict) -> Unit) {
    private var thread: HandlerThread? = null
    private val main = Handler(Looper.getMainLooper())

    // Frames before this (System.nanoTime) aren't counted: the app's first seconds are slow on any
    // phone while its drawing is set up, and the first second back at the table after a pause
    @Volatile private var countFrom = Long.MAX_VALUE
    @Volatile private var recording = false
    // One refresh of the screen, but never less than a 60th of a second, since the app asks for 60
    // frames a second at most (a screen with no 60 Hz mode runs faster than the game needs)
    @Volatile private var refreshNs = SIXTY_HZ_NS
    // The vsync of the last frame counted (on the listener's thread), 0 after a pause
    @Volatile private var lastVsync = 0L

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        if (!recording || Run.decided) return@OnFrameMetricsAvailableListener
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        // Until the tiles' images are made (in the background, slower on a slow phone), tiles are
        // drawn shape by shape: not how the game draws, so not counted
        if (!TileRenderer.TileAtlas.made) return@OnFrameMetricsAvailableListener
        if (metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) < countFrom) return@OnFrameMetricsAvailableListener
        val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
        val gap = if (lastVsync > 0) vsync - lastVsync else 0L
        lastVsync = vsync
        Run.tally.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION), metrics.getMetric(FrameMetrics.DEADLINE), refreshNs, gap)
        val verdict = Run.tally.verdict() ?: return@OnFrameMetricsAvailableListener
        Run.decided = true
        Log.i(TAG, "verdict: $verdict")
        main.post {
            release()
            onVerdict(verdict)
        }
    }

    /** Frames are counted while on: a game in play, with the check still to run. */
    fun setRecording(on: Boolean) {
        if (Run.decided || on == recording) return
        if (on) {
            if (thread == null) {
                val t = HandlerThread("FrameWatch").apply { start() }
                thread = t
                window.addOnFrameMetricsAvailableListener(listener, Handler(t.looper))
            }
            val refresh = window.decorView.display?.refreshRate ?: 60f
            refreshNs = maxOf((1e9 / refresh).toLong(), SIXTY_HZ_NS)
            val skip = if (Run.warmedUp) RESUME_SKIP_NS else WARM_UP_NS
            Run.warmedUp = true
            countFrom = System.nanoTime() + skip
            lastVsync = 0L
        }
        recording = on
        if (BuildConfig.PERF_HOOKS) Log.i(TAG, if (on) "counting frames (refresh ${refreshNs / 1000} us)" else "paused")
    }

    /** Stops listening (the activity is going, or the check is done). */
    fun release() {
        recording = false
        val t = thread ?: return
        thread = null
        try {
            window.removeOnFrameMetricsAvailableListener(listener)
        } catch (_: IllegalArgumentException) {
        }
        t.quitSafely()
    }

    // What lasts as long as the app does
    private object Run {
        val tally = FrameTally()
        @Volatile var decided = false
        @Volatile var warmedUp = false
    }

    private companion object {
        const val TAG = "FrameWatch"
        const val WARM_UP_NS = 5_000_000_000L
        const val RESUME_SKIP_NS = 1_000_000_000L
        const val SIXTY_HZ_NS = 16_666_667L
    }
}
