package com.vortessence.mahjong.data.model

/** Newer Play version (Play builds only). Download progress is 0..1; Ready installs on restart. */
sealed interface UpdateNotice {
    data object Available : UpdateNotice
    data class Downloading(val progress: Float) : UpdateNotice
    data object Ready : UpdateNotice
}
