package com.vortessence.mahjong

import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TilePosition
import com.vortessence.mahjong.engine.BoardSolvabilityGenerator

// Tests only: play never checks whether a board can still be cleared, only whether a pair is left
/**
 * Exhaustively checks whether [tiles] can be cleared completely.
 * Returns null if no verdict was reached within [maxStates] explored board states.
 */
fun canBeCleared(tiles: List<Pair<TilePosition, Tile>>, maxStates: Int = 20_000): Boolean? {
    val n = tiles.size
    if (n == 0) return true
    if (n >= Long.SIZE_BITS) return null

    // Precompute blocking relations as bitmasks of tile indices
    val above = LongArray(n)
    val left = LongArray(n)
    val right = LongArray(n)
    for (i in 0 until n) {
        for (j in 0 until n) {
            if (i == j) continue
            val a = tiles[i].first
            val b = listOf(tiles[j].first)
            val bit = 1L shl j
            if (BoardSolvabilityGenerator.isCovered(a, b)) above[i] = above[i] or bit
            if (BoardSolvabilityGenerator.isBlockedLeft(a, b)) left[i] = left[i] or bit
            if (BoardSolvabilityGenerator.isBlockedRight(a, b)) right[i] = right[i] or bit
        }
    }

    val deadEnds = HashSet<Long>()
    var explored = 0

    fun search(mask: Long): Boolean? {
        if (mask == 0L) return true
        if (mask in deadEnds) return false
        if (++explored > maxStates) return null

        val free = (0 until n).filter { i ->
            mask and (1L shl i) != 0L &&
                mask and above[i] == 0L &&
                (mask and left[i] == 0L || mask and right[i] == 0L)
        }
        for (x in free.indices) {
            for (y in (x + 1) until free.size) {
                val i = free[x]
                val j = free[y]
                if (!tiles[i].second.matches(tiles[j].second)) continue
                when (search(mask and ((1L shl i) or (1L shl j)).inv())) {
                    true -> return true
                    null -> return null
                    false -> {}
                }
            }
        }
        deadEnds.add(mask)
        return false
    }

    return search((1L shl n) - 1)
}
