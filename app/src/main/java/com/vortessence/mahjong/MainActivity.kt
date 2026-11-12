/*
 * Dark Mahjong: Copyright (C) 2026 Grevillea Labs, based on Mahjong Solitaire, Copyright (C) 2026
 * PcmciaKai. Free software under the GNU GPL v3 with the App Store Exception; see LICENSE and
 * NOTICE.md. The names "Dark Mahjong" and 暗夜麻將, the name seal and the launcher icon are not
 * licensed for use in modified versions (GPLv3 section 7(e)).
 *
 * Dark Mahjong is made to be free of ads and tracking, offline and open source, with nothing to buy:
 * the Google Play build only offers optional tips through Google Play, which unlock nothing, and the
 * open-source build has none. If you build on it, please keep it that way: no ads, nothing locked
 * behind a payment, not sold for profit.
 */
package com.vortessence.mahjong

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vortessence.mahjong.perf.FrameWatch
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.ui.components.PerfSwitches
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.viewmodel.MahjongViewModel
import com.vortessence.mahjong.tips.Tips
import com.vortessence.mahjong.update.AppUpdates
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private val viewModel: MahjongViewModel by viewModels()
    private lateinit var updates: AppUpdates
    private lateinit var tips: Tips
    private lateinit var frameWatch: FrameWatch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TileWear.prepareAsync()
        ConstellationRepository.prepareAsync()
        // Play builds ask Google Play for newer versions; the open-source build never does
        updates = AppUpdates(this)
        // Play builds can take optional tips through Google Play; the open-source build has none
        tips = Tips(this)
        enableEdgeToEdge()
        // Full-screen game: system bars stay hidden and reappear briefly on an edge swipe
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // 60 frames a second at most: plenty for a calm board game, where 90 or 120 would only
        // spend battery. The screen's 60 Hz mode at its current resolution, if it has one
        display?.let { screen ->
            val current = screen.mode
            screen.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .minByOrNull { abs(it.refreshRate - 60f) }
                ?.let { mode -> window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId } }
        }
        setContent {
            MahjongApp(viewModel = viewModel, updates = updates, tips = tips)
        }
        // The first-game performance check: frames are timed while a game is in play, until it
        // has decided (once per install)
        frameWatch = FrameWatch(window) { viewModel.onFrameVerdict(it) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    viewModel.measuringFrames.collect { frameWatch.setRecording(it) }
                } finally {
                    frameWatch.setRecording(false)
                }
            }
        }
        // Only on a fresh start: a recreated activity (a dark-mode switch) keeps the game in play
        if (savedInstanceState == null) openPerfScene(intent)
    }

    override fun onDestroy() {
        frameWatch.release()
        super.onDestroy()
    }

    // Test builds only: adb shell am start -S -n com.formlessform.darkmahjong/com.vortessence.mahjong.MainActivity
    //   --es perfLayout Quad --ez perfFire true [--es perfSurface WALNUT]
    // (-S stops the app first, so the scene opens on a fresh start)
    private fun openPerfScene(intent: Intent?) {
        if (!BuildConfig.PERF_HOOKS) return
        // --ez perfCheckReset true: the first-game performance check runs again, as on a new install
        // (with a perf scene, the scene's frames are the ones timed)
        val checkReset = intent?.getBooleanExtra("perfCheckReset", false) == true
        if (checkReset) viewModel.resetPerfCheck()
        PerfSwitches.off = intent?.getStringExtra("perfOff")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        val layout = intent?.getStringExtra("perfLayout") ?: return
        PerfSwitches.fireFps = intent.getIntExtra("perfFireFps", 0)
        val surface = intent.getStringExtra("perfSurface")?.let { name -> TableSurface.entries.firstOrNull { it.name == name } }
        viewModel.startPerfScene(layout, intent.getBooleanExtra("perfFire", false), surface, intent.getBooleanExtra("perfAutoplay", false), countsForCheck = checkReset)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }
}
