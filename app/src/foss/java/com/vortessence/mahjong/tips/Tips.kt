package com.vortessence.mahjong.tips

import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Open-source build: nothing to buy, so no tips and no Settings tip row. */
@Suppress("UNUSED_PARAMETER")
class Tips(activity: ComponentActivity) {
    val offers: StateFlow<List<TipOffer>> = MutableStateFlow(emptyList())
    val thanked: StateFlow<Boolean> = MutableStateFlow(false)
    val available = false

    fun refresh() {}

    fun tip(offer: TipOffer) {}

    fun clearThanks() {}
}
