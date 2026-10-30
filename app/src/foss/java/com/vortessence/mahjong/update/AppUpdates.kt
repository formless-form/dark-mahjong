package com.vortessence.mahjong.update

import androidx.activity.ComponentActivity
import com.vortessence.mahjong.data.model.UpdateNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The open-source build has no store to ask, so it never mentions an update (the Play build's
 * AppUpdates asks Google Play).
 */
@Suppress("UNUSED_PARAMETER")
class AppUpdates(activity: ComponentActivity) {
    val notice: StateFlow<UpdateNotice?> = MutableStateFlow(null)

    fun act() {}
}
