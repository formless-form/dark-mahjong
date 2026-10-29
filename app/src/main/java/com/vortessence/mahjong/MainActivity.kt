/*
 * Dark Mahjong: Copyright (C) 2026 Grevillea Labs, based on Mahjong Solitaire, Copyright (C) 2026
 * PcmciaKai. Free software under the GNU GPL v3 with the App Store Exception; see LICENSE and
 * NOTICE.md. The names "Dark Mahjong" and 暗夜麻將, the name seal and the launcher icon are not
 * licensed for use in modified versions (GPLv3 section 7(e)).
 *
 * Dark Mahjong is made to be free of ads and tracking, with nothing to buy, offline and open source. If you
 * build on it, please keep it that way: no ads, no in-app purchases, not sold for profit.
 */
package com.vortessence.mahjong

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.ui.components.PerfSwitches
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.viewmodel.MahjongViewModel
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private val viewModel: MahjongViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TileWear.prepareAsync()
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
            MahjongApp(viewModel = viewModel)
        }
        openPerfScene(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPerfScene(intent)
    }

    // Test builds only: adb shell am start -n com.formlessform.darkmahjong/com.vortessence.mahjong.MainActivity
    //   --es perfLayout Quad --ez perfFire true [--es perfSurface WALNUT]
    private fun openPerfScene(intent: Intent?) {
        if (!BuildConfig.PERF_HOOKS) return
        PerfSwitches.off = intent?.getStringExtra("perfOff")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        val layout = intent?.getStringExtra("perfLayout") ?: return
        PerfSwitches.fireFps = intent.getIntExtra("perfFireFps", 0)
        val surface = intent.getStringExtra("perfSurface")?.let { name -> TableSurface.entries.firstOrNull { it.name == name } }
        viewModel.startPerfScene(layout, intent.getBooleanExtra("perfFire", false), surface, intent.getBooleanExtra("perfAutoplay", false))
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
