package com.vortessence.mahjong.tips

import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The open-source build has nothing to buy, tips included, so it offers none and Settings shows no
 * tip row (the Play build's Tips goes through Google Play).
 */
@Suppress("UNUSED_PARAMETER")
class Tips(activity: ComponentActivity) {
    val offers: StateFlow<List<TipOffer>> = MutableStateFlow(emptyList())
    val thanked: StateFlow<Boolean> = MutableStateFlow(false)
    val available = false

    fun refresh() {}

    fun tip(offer: TipOffer) {}

    fun clearThanks() {}
}
