package com.vortessence.mahjong.data.model

/**
 * A newer version of the app on Google Play (Play builds only): there to fetch, being fetched
 * ([progress] 0..1), or fetched and waiting for a restart to install.
 */
sealed interface UpdateNotice {
    data object Available : UpdateNotice
    data class Downloading(val progress: Float) : UpdateNotice
    data object Ready : UpdateNotice
}
