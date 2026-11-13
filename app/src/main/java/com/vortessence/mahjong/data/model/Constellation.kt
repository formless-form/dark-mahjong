package com.vortessence.mahjong.data.model

data class Constellation(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val description: String,
    val positions: List<TilePosition>,
    // Predefined tile for each entry in [positions]; null means tiles are dealt randomly
    val fixedTiles: List<Tile>? = null,
    // A grouping for imported layouts, e.g. "Animals"; null for Dark Mahjong's own
    val category: String? = null,
    // Too small held upright, so always played on its side (Mah's boards kept in 1.4.16), whatever
    // the rule for wide boards says (isWideBoard)
    val onSide: Boolean = false,
) {
    val tileCount: Int get() = positions.size
}
