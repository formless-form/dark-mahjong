package com.vortessence.mahjong.data.repository

import androidx.test.core.app.ApplicationProvider
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.PerfCheck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The performance check and Low-performance mode stay with the phone they were set on. */
@RunWith(RobolectricTestRunner::class)
class PerfPhoneTest {
    private val repo = PreferencesRepository(ApplicationProvider.getApplicationContext())

    // The store outlives a test, so each starts from nothing stored
    private suspend fun slowAndSwitched() {
        repo.resetPerfCheck()
        repo.storedOnPhone(null)
        repo.setPerfCheck(PerfCheck.SLOW, 40)
        repo.updateSettings(GameSettings(), GameSettings(lowPerformance = true))
        repo.setPerfCheck(PerfCheck.SWITCHED)
    }

    @Test
    fun keptOnThisPhone() = runBlocking {
        slowAndSwitched()
        repo.namePerfPhone()
        assertEquals(PerfCheck.SWITCHED, repo.perfCheckFlow.first().check)
        assertEquals(40, repo.perfCheckFlow.first().stutterPercent)
        assertTrue(repo.settingsFlow.first().lowPerformance)
    }

    @Test
    fun setAsideOnAnotherPhone() = runBlocking {
        slowAndSwitched()
        repo.storedOnPhone("Elsewhere/Old phone")
        repo.namePerfPhone()
        assertEquals(PerfCheck.PENDING, repo.perfCheckFlow.first().check)
        assertFalse(repo.settingsFlow.first().lowPerformance)
        // This phone's own verdict replaces the other's, and the other's mode doesn't come back
        repo.setPerfCheck(PerfCheck.PASSED, 2)
        assertEquals(PerfCheck.PASSED, repo.perfCheckFlow.first().check)
        assertFalse(repo.settingsFlow.first().lowPerformance)
    }

    @Test
    fun turnedOnByHandOnTheNewPhone() = runBlocking {
        slowAndSwitched()
        repo.storedOnPhone("Elsewhere/Old phone")
        repo.updateSettings(GameSettings(), GameSettings(lowPerformance = true))
        assertTrue(repo.settingsFlow.first().lowPerformance)
        // The player's own choice: the check is never run
        assertEquals(PerfCheck.MANUAL, repo.perfCheckFlow.first().check)
    }

    @Test
    fun aPassFromTheFirstCheckIsCheckedAgain() = runBlocking {
        repo.resetPerfCheck()
        repo.setPerfCheck(PerfCheck.PASSED, 0)
        assertEquals(PerfCheck.PASSED, repo.perfCheckFlow.first().check)
        // As 1.4.11 and 1.4.12 stored it, before passes near the line were told apart
        repo.storedCheckVersion(null)
        assertEquals(PerfCheck.PENDING, repo.perfCheckFlow.first().check)
        repo.setPerfCheck(PerfCheck.PASSED_CLOSE, 0)
        assertEquals(PerfCheck.PASSED_CLOSE, repo.perfCheckFlow.first().check)
    }

    @Test
    fun storedBeforePhonesWereNamedIsThisPhones() = runBlocking {
        slowAndSwitched()
        assertEquals(PerfCheck.SWITCHED, repo.perfCheckFlow.first().check)
        assertTrue(repo.settingsFlow.first().lowPerformance)
    }
}
