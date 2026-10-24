package dev.flip2dnd.next.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorMonitoringSessionTest {
    private class Hardware(
        var tiltAvailable: Boolean = true,
        var wakeAcceleration: Boolean = true,
        var acceleration: Boolean = true,
    ) : SensorMonitoringSession.Hardware {
        var tiltArmed = false
        var sampling = false
        var held = false
        var acquisitions = 0
        var sampleStarts = 0
        var batched = false
        override fun armTilt(): Boolean { tiltArmed = tiltAvailable; return tiltArmed }
        override fun disarmTilt() { tiltArmed = false }
        override fun startAcceleration(wakeUp: Boolean, batch: Boolean): Boolean {
            sampling = if (wakeUp) wakeAcceleration else acceleration
            if (sampling) sampleStarts++
            batched = batch
            return sampling
        }
        override fun stopAcceleration() { sampling = false }
        override fun acquireWakeLock(timeoutMs: Long) { assertTrue(timeoutMs > 0); held = true; acquisitions++ }
        override fun releaseWakeLock() { held = false }
    }

    @Test fun hoursOnATableNeedNoCpuLeaseOrAccelerationStreamAfterSnapshot() = runTest {
        val hw = Hardware()
        val session = SensorMonitoringSession(this, hw)
        session.start(screenOff = true)
        runCurrent()
        assertTrue(hw.held)
        advanceTimeBy(SensorMonitoringSession.SNAPSHOT_MS)
        runCurrent()
        assertFalse(hw.held)
        assertFalse(hw.sampling)
        assertTrue(hw.tiltArmed)
        val acquisitions = hw.acquisitions
        val starts = hw.sampleStarts
        advanceTimeBy(4 * 60 * 60 * 1000L)
        runCurrent()
        assertEquals(acquisitions, hw.acquisitions)
        assertEquals(starts, hw.sampleStarts)
        session.stop()
        assertFalse(hw.tiltArmed)
    }

    @Test fun pickupAfterHoursStartsFreshSamplingAndReleasesTheLeaseAgain() = runTest {
        val hw = Hardware()
        val session = SensorMonitoringSession(this, hw)
        session.start(true)
        advanceTimeBy(3 * 60 * 60 * 1000L)
        session.onTilt()
        runCurrent()
        assertTrue(hw.sampling)
        assertTrue(hw.held)
        advanceTimeBy(SensorMonitoringSession.SNAPSHOT_MS)
        runCurrent()
        assertFalse(hw.sampling)
        assertFalse(hw.held)
        session.stop()
    }

    @Test fun longActivationDelayKeepsSamplingSoPickupCanStillCancelIt() = runTest {
        val hw = Hardware()
        val session = SensorMonitoringSession(this, hw)
        session.start(true)
        val job = launch { session.confirm { delay(10_000) } }
        runCurrent()
        advanceTimeBy(6000)
        session.onTilt()
        runCurrent()
        assertTrue(hw.sampling)
        assertTrue(hw.held)
        job.cancel()
        runCurrent()
        advanceTimeBy(SensorMonitoringSession.SNAPSHOT_MS)
        runCurrent()
        assertFalse(hw.sampling)
        assertFalse(hw.held)
        session.stop()
    }

    @Test fun repeatedTiltsExtendOnlyTheShortSnapshot() = runTest {
        val hw = Hardware()
        val session = SensorMonitoringSession(this, hw)
        session.start(true)
        advanceTimeBy(3000)
        session.onTilt()
        runCurrent()
        advanceTimeBy(2000)
        runCurrent()
        assertTrue(hw.sampling)
        assertTrue(hw.held)
        advanceTimeBy(2000)
        runCurrent()
        assertFalse(hw.sampling)
        assertFalse(hw.held)
        session.stop()
    }

    @Test fun screenOnDuringSnapshotCannotBeStoppedByOldSnapshotCleanup() = runTest {
        val hw = Hardware()
        val session = SensorMonitoringSession(this, hw)
        session.start(true)
        runCurrent()
        session.setScreenOff(false)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(SensorMonitoringSession.Mode.SCREEN_ON, session.mode.value)
        assertTrue(hw.sampling)
        assertFalse(hw.held)
        assertFalse(hw.tiltArmed)
        session.stop()
        assertFalse(hw.sampling)
    }

    @Test fun missingOrRejectedTiltUsesBatchedWakeSensorWithoutAWakeLock() = runTest {
        val hw = Hardware(tiltAvailable = false)
        val session = SensorMonitoringSession(this, hw)
        session.start(true)
        advanceTimeBy(4 * 60 * 60 * 1000L)
        assertEquals(SensorMonitoringSession.Mode.WAKE_UP_ACCELEROMETER, session.mode.value)
        assertTrue(hw.sampling)
        assertTrue(hw.batched)
        assertEquals(0, hw.acquisitions)
        session.stop()
    }

    @Test fun unsupportedHardwareUsesExplicitLegacyModeOnlyWhileMonitoring() = runTest {
        val hw = Hardware(tiltAvailable = false, wakeAcceleration = false)
        val session = SensorMonitoringSession(this, hw)
        session.start(true)
        advanceTimeBy(130_000)
        runCurrent()
        assertEquals(SensorMonitoringSession.Mode.LEGACY, session.mode.value)
        assertTrue(hw.held)
        assertTrue(hw.acquisitions > 1)
        session.stop()
        advanceTimeBy(180_000)
        runCurrent()
        assertFalse(hw.held)
        assertFalse(hw.sampling)
        assertEquals(SensorMonitoringSession.Mode.STOPPED, session.mode.value)
    }

    @Test fun failedSnapshotCanFallBackToTheWakeUpAccelerometer() = runTest {
        val hw = Hardware(acceleration = false)
        val session = SensorMonitoringSession(this, hw)
        session.start(true)
        runCurrent()
        assertEquals(SensorMonitoringSession.Mode.WAKE_UP_ACCELEROMETER, session.mode.value)
        assertTrue(hw.sampling)
        assertFalse(hw.held)
        assertFalse(hw.tiltArmed)
        session.stop()
    }

    @Test fun noAccelerometerDoesNotAcquireAFallbackWakeLock() = runTest {
        val hw = Hardware(tiltAvailable = false, wakeAcceleration = false, acceleration = false)
        val session = SensorMonitoringSession(this, hw)
        session.start(true)
        advanceTimeBy(60_000)
        assertEquals(SensorMonitoringSession.Mode.UNAVAILABLE, session.mode.value)
        assertEquals(0, hw.acquisitions)
        session.stop()
    }

    @Test fun screenOnRegistrationFailureIsReportedAndCanBeRetried() = runTest {
        val hw = Hardware(tiltAvailable = false, wakeAcceleration = false, acceleration = false)
        val session = SensorMonitoringSession(this, hw)
        session.start(false)
        assertEquals(SensorMonitoringSession.Mode.UNAVAILABLE, session.mode.value)
        hw.acceleration = true
        session.start(false)
        assertEquals(SensorMonitoringSession.Mode.SCREEN_ON, session.mode.value)
        session.stop()
    }

    @Test fun wakeAccelerometerCanMonitorWithScreenOnWhenOrdinaryRegistrationFails() = runTest {
        val hw = Hardware(acceleration = false)
        val session = SensorMonitoringSession(this, hw)
        session.start(false)
        assertEquals(SensorMonitoringSession.Mode.SCREEN_ON, session.mode.value)
        assertFalse(hw.held)
        session.stop()
    }

    @Test fun stoppingDuringSnapshotReleasesEverythingAndIgnoresLateTilt() = runTest {
        val hw = Hardware()
        val session = SensorMonitoringSession(this, hw)
        session.start(true)
        runCurrent()
        session.stop()
        session.onTilt()
        advanceUntilIdle()
        assertFalse(hw.sampling)
        assertFalse(hw.held)
        assertFalse(hw.tiltArmed)
    }
}
