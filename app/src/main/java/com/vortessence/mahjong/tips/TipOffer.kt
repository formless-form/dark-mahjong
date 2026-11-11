package com.vortessence.mahjong.tips

/** One tip a player can leave: its Play product and the price Google Play shows for it. */
data class TipOffer(val productId: String, val price: String)

/** The tips, smallest first: Play products made in Play Console with these ids (consumable). */
val TIP_PRODUCTS = listOf("tip_small", "tip_medium", "tip_large")
