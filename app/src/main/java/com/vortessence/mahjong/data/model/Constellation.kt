package com.vortessence.mahjong.data.model

data class Constellation(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val description: String,
    val positions: List<TilePosition>,
    // One per position; null = dealt randomly
    val fixedTiles: List<Tile>? = null,
    // Imported layouts' group, e.g. "Animals"; null for our own
    val category: String? = null,
    // Too small upright: always on its side, whatever isWideBoard says (Mah's boards, 1.4.16)
    val onSide: Boolean = false,
    // Opening matches that can't lose the board; above 1, every deal is checked, not just narrow ones
    val safeMoves: Int = 1,
) {
    val tileCount: Int get() = positions.size
}
