@file:Suppress("unused")

package com.vortessence.mahjong.data.model

enum class TileSuit(val displayName: String) {
    CHARACTER("Characters"),
    BAMBOO("Bamboo"),
    CIRCLE("Circles"),
    WIND("Winds"),
    DRAGON("Dragons"),
    SEASON("Seasons"),
    FLOWER("Flowers")
}

data class Tile(
    val suit: TileSuit,
    val value: Int, // 1..9 suits; 1..3 Dragons; 1..4 Winds, Seasons, Flowers
) {
    /** Same suit and value; any Season matches any Season, any Flower any Flower. */
    fun matches(other: Tile): Boolean {
        if ((suit == TileSuit.SEASON) && (other.suit == TileSuit.SEASON)) return true
        if ((suit == TileSuit.FLOWER) && (other.suit == TileSuit.FLOWER)) return true
        return (suit == other.suit) && (value == other.value)
    }

    val identifier: String
        get() = "${suit.name}_$value"

    companion object {
        const val WIND_EAST = 1
        const val WIND_SOUTH = 2
        const val WIND_WEST = 3
        const val WIND_NORTH = 4

        const val DRAGON_RED = 1
        const val DRAGON_GREEN = 2
        const val DRAGON_WHITE = 3

        const val SEASON_SPRING = 1
        const val SEASON_SUMMER = 2
        const val SEASON_AUTUMN = 3
        const val SEASON_WINTER = 4

        const val FLOWER_PLUM = 1
        const val FLOWER_ORCHID = 2
        const val FLOWER_CHRYSANTHEMUM = 3
        const val FLOWER_BAMBOO = 4

        fun createStandard144Set(): List<Tile> {
            val tiles = mutableListOf<Tile>()
            for (v in 1..9) {
                repeat(4) { tiles.add(Tile(TileSuit.CHARACTER, v)) }
            }
            for (v in 1..9) {
                repeat(4) { tiles.add(Tile(TileSuit.BAMBOO, v)) }
            }
            for (v in 1..9) {
                repeat(4) { tiles.add(Tile(TileSuit.CIRCLE, v)) }
            }
            for (v in 1..4) {
                repeat(4) { tiles.add(Tile(TileSuit.WIND, v)) }
            }
            for (v in 1..3) {
                repeat(4) { tiles.add(Tile(TileSuit.DRAGON, v)) }
            }
            for (v in 1..4) {
                tiles.add(Tile(TileSuit.SEASON, v))
            }
            for (v in 1..4) {
                tiles.add(Tile(TileSuit.FLOWER, v))
            }
            return tiles
        }
    }
}
