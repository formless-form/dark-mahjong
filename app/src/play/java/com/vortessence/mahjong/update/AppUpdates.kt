package com.vortessence.mahjong.update

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.vortessence.mahjong.data.model.UpdateNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Asks Google Play for a newer version for the title screen to mention; on tap, downloads it in the
 * background and installs with a restart. The Play Store app does the asking (the game has no
 * internet access); sideloaded copies never hear of one. Made in onCreate, as it registers for
 * Play's confirmation screen.
 */
class AppUpdates(activity: ComponentActivity) : DefaultLifecycleObserver {
    private val manager = AppUpdateManagerFactory.create(activity)

    private val state = MutableStateFlow<UpdateNotice?>(null)
    val notice: StateFlow<UpdateNotice?> = state.asStateFlow()

    // Play's confirmation before downloading; offered again if declined or failed
    private val confirmation = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) check()
    }

    private val progress = InstallStateUpdatedListener { install ->
        when (install.installStatus()) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING ->
                state.value = UpdateNotice.Downloading(fraction(install.bytesDownloaded(), install.totalBytesToDownload()))
            InstallStatus.DOWNLOADED -> state.value = UpdateNotice.Ready
            InstallStatus.INSTALLING, InstallStatus.INSTALLED -> state.value = null
            InstallStatus.FAILED, InstallStatus.CANCELED -> check()
            else -> Unit
        }
    }

    init {
        manager.registerListener(progress)
        activity.lifecycle.addObserver(this)
    }

    // A release may have come out meanwhile
    override fun onResume(owner: LifecycleOwner) = check()

    override fun onDestroy(owner: LifecycleOwner) = manager.unregisterListener(progress)

    /** Title-screen tap: fetch the update, or install the fetched one. */
    fun act() {
        when (state.value) {
            UpdateNotice.Available -> manager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.canUpdate()) {
                    manager.startUpdateFlowForResult(info, confirmation, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
                }
            }
            UpdateNotice.Ready -> manager.completeUpdate()
            else -> Unit
        }
    }

    private fun check() {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                state.value = when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> UpdateNotice.Ready
                    (info.installStatus() == InstallStatus.DOWNLOADING) || (info.installStatus() == InstallStatus.PENDING) ->
                        UpdateNotice.Downloading(fraction(info.bytesDownloaded(), info.totalBytesToDownload()))
                    info.canUpdate() -> UpdateNotice.Available
                    else -> null
                }
            }
            // No Play Store, not from Google Play, or offline
            .addOnFailureListener { state.value = null }
    }

    private fun AppUpdateInfo.canUpdate() =
        (updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) && isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

    private fun fraction(done: Long, total: Long) = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}
