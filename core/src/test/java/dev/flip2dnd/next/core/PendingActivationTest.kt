package dev.flip2dnd.next.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingActivationTest {
    @Test fun explicitPauseWaitsForThePendingLeaseToFinishBeforeRestoration() = runTest {
        var held = false
        var activated = false
        val pending = PendingActivation(this) { action ->
            held = true
            try { action() } finally { held = false }
        }
        pending.startIfIdle(10_000) { activated = true }
        runCurrent()
        pending.cancelAndWait()
        assertEquals(false, held)
        advanceUntilIdle()
        assertEquals(false, activated)
    }

    @Test fun activationLeaseCoversDelayAndIsReleasedOnPickup() = runTest {
        var held = false
        val pending = PendingActivation(this) { action ->
            held = true
            try { action() } finally { held = false }
        }
        pending.startIfIdle(10_000) { assertEquals(true, held) }
        runCurrent()
        assertEquals(true, held)
        advanceTimeBy(1000)
        pending.cancel()
        runCurrent()
        assertEquals(false, held)
    }

    @Test fun repeatedScreenAndProximityEventsDoNotPostponeActivation() = runTest {
        val pending = PendingActivation(this)
        var activations = 0
        pending.startIfIdle(2000) { activations++ }
        runCurrent()
        repeat(3) {
            advanceTimeBy(500)
            pending.startIfIdle(2000) { activations++ }
        }
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, activations)
        assertEquals(2000, currentTime)
    }

    @Test fun pickupOrFilterCancelsPendingActivationAndNextFlipCanRetry() = runTest {
        val pending = PendingActivation(this)
        var activations = 0
        pending.startIfIdle(2000) { activations++ }
        advanceTimeBy(1000)
        pending.cancel()
        advanceUntilIdle()
        assertEquals(0, activations)
        pending.startIfIdle(2000) { activations++ }
        advanceUntilIdle()
        assertEquals(1, activations)
    }
}
