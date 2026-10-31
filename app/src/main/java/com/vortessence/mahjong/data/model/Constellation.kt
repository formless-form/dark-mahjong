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
) {
    val tileCount: Int get() = positions.size
}
