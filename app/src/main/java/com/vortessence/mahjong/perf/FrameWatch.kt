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
 * Times the first game's frames, once, into an app-lifetime [FrameTally] (a recreated activity
 * carries on). Metrics are read on their own thread; the verdict comes back once on the main thread.
 */
class FrameWatch(private val window: Window, private val onVerdict: (FrameVerdict) -> Unit) {
    private var thread: HandlerThread? = null
    private val main = Handler(Looper.getMainLooper())

    // System.nanoTime; skips the slow first seconds of the app, and the first second after a pause
    @Volatile private var countFrom = Long.MAX_VALUE
    @Volatile private var recording = false
    // At least 1/60 s: the app asks for 60 fps at most, though some screens have no 60 Hz mode
    @Volatile private var refreshNs = SIXTY_HZ_NS
    // Last counted frame's vsync (listener thread); 0 after a pause
    @Volatile private var lastVsync = 0L

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        if (!recording || Run.decided) return@OnFrameMetricsAvailableListener
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        // Until the tile atlas is made, tiles are drawn shape by shape: not representative
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

    /** On while a game is in play and the check is still to run. */
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

    // App lifetime
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
