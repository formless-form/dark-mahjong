package com.vortessence.mahjong.data.repository

import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.Difficulty
import com.vortessence.mahjong.data.model.TilePosition

/**
 * A layout from Mah by ffalt (MIT licence, see NOTICE.md), as listed in [MahLayouts]. [map] is
 * Mah's compact format: `[[z, [[y, [x | [x, count], ...]], ...]], ...]` in half-tile units, the
 * same grid as Dark Mahjong's own layouts, where `[x, count]` is a run of tiles side by side.
 */
internal class MahBoard(
    val id: String,
    val name: String,
    val category: String,
    val difficulty: Difficulty,
    val isTall: Boolean,
    val map: String,
) {
    fun toConstellation() = Constellation(
        id = id,
        name = name,
        difficulty = difficulty,
        description = "",
        positions = positions(),
        isPortraitOptimized = isTall,
        category = category,
    )

    fun positions(): List<TilePosition> {
        val tiles = mutableListOf<TilePosition>()
        for (layer in parse(map) as List<*>) {
            val (z, rows) = layer as List<*>
            for (row in rows as List<*>) {
                val (y, items) = row as List<*>
                val runs = items as? List<*> ?: listOf(items)
                for (item in runs) {
                    if (item is List<*>) {
                        val x = item[0] as Int
                        repeat(item[1] as Int) { i -> tiles += pos(x + 2 * i, y as Int, z as Int) }
                    } else {
                        tiles += pos(item as Int, y as Int, z as Int)
                    }
                }
            }
        }
        return tiles
    }

    private fun pos(x: Int, y: Int, z: Int) = TilePosition(x.toFloat(), y.toFloat(), z)

    private companion object {
        // Just enough JSON for nested arrays of whole numbers
        fun parse(text: String): Any {
            var i = 0
            fun value(): Any {
                if (text[i] == '[') {
                    i++
                    val list = mutableListOf<Any>()
                    while (text[i] != ']') {
                        list += value()
                        if (text[i] == ',') i++
                    }
                    i++
                    return list
                }
                val start = i
                while (i < text.length && (text[i] == '-' || text[i].isDigit())) i++
                return text.substring(start, i).toInt()
            }
            return value()
        }
    }
}
