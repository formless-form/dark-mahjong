package com.vortessence.mahjong.data.model

// Order matters: the table shader selects the surface by ordinal
enum class TableSurface(val displayName: String) {
    GREEN_MAT("Green mat"),
    WALNUT("Walnut"),
    THAI_STRAW("Thai straw"),
}

// How the table is lit. Order matters: the table shader selects the lighting by ordinal
enum class TableLighting(val displayName: String) {
    LAMP("Lamp"),
    CANDLE("Candle"),
    FIREPLACE("Fireplace"),
}

@Suppress("unused")
typealias AppTheme = TableSurface

data class GameSettings(
    val soundEnabled: Boolean = true,
    val soundVolume: Float = 1.0f,
    val musicEnabled: Boolean = true,
    val musicVolume: Float = 0.4f,
    val autoZoomEnabled: Boolean = true,
    val highlightFreeTiles: Boolean = false,
    val animationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showCornerIndex: Boolean = false,
    val tableSurface: TableSurface = TableSurface.GREEN_MAT,
    val lighting: TableLighting = TableLighting.LAMP,
    // How brightly the fireplace burns, 0..1; the middle is its natural strength
    val fireIntensity: Float = 0.5f,
) {
    val theme: TableSurface get() = tableSurface
}
