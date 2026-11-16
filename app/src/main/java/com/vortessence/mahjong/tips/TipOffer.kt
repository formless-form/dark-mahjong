package com.vortessence.mahjong.tips

/** A tip's Play product and its Google Play price. */
data class TipOffer(val productId: String, val price: String)

/** Smallest first; consumable products made in Play Console with these ids. */
val TIP_PRODUCTS = listOf("tip_small", "tip_medium", "tip_large")
