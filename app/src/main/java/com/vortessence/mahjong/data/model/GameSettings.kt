package com.vortessence.mahjong.data.model

// Order matters: the table shader selects the surface by ordinal
enum class TableSurface(val displayName: String) {
    GREEN_MAT("Green mat"),
    WALNUT("Walnut"),
    THAI_STRAW("Thai straw"),
}

@Suppress("unused")
typealias AppTheme = TableSurface

data class GameSettings(
    val soundEnabled: Boolean = true,
    val soundVolume: Float = 0.7f,
    val musicEnabled: Boolean = true,
    val musicVolume: Float = 0.6f,
    val autoZoomEnabled: Boolean = true,
    val highlightFreeTiles: Boolean = false,
    val animationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showCornerIndex: Boolean = false,
    val tableSurface: TableSurface = TableSurface.GREEN_MAT,
    // The lamp over the table is always lit; how brightly, 0..1, the middle its natural strength
    val lampBrightness: Float = 0.5f,
    // A fire in a hearth beside the table, lit or not, and how brightly it burns (as the lamp)
    val fireplaceLit: Boolean = false,
    val fireIntensity: Float = 0.5f,
) {
    val theme: TableSurface get() = tableSurface
}
