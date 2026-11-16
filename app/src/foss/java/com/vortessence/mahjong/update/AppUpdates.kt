package com.vortessence.mahjong.update

import androidx.activity.ComponentActivity
import com.vortessence.mahjong.data.model.UpdateNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Open-source build: no store to ask, so never mentions an update. */
@Suppress("UNUSED_PARAMETER")
class AppUpdates(activity: ComponentActivity) {
    val notice: StateFlow<UpdateNotice?> = MutableStateFlow(null)

    fun act() {}
}
