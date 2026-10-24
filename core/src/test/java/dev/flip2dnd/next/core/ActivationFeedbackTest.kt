package dev.flip2dnd.next.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivationFeedbackTest {
    @Test fun exitWaveformStartsAfterTheDndTransitionHasSettled() = runTest {
        val events = mutableListOf<String>()
        launch {
            ActivationFeedback.transition(false, { events += "DND off" }, { events += "selected off pattern" })
        }
        runCurrent()
        assertEquals(listOf("DND off"), events)
        advanceTimeBy(249)
        runCurrent()
        assertEquals(listOf("DND off"), events)
        advanceUntilIdle()
        assertEquals(listOf("DND off", "selected off pattern"), events)
        assertEquals(250, testScheduler.currentTime)
    }
    @Test fun totalSilenceWaitsForFeedbackBeforeMuting() = runTest {
        val events = mutableListOf<String>()
        launch { ActivationFeedback.transition(true, { events += "mute" }, { events += "feedback" }) }
        runCurrent()
        assertEquals(listOf("feedback"), events)
        advanceUntilIdle()
        assertEquals(listOf("feedback", "mute"), events)
        assertEquals(2000, testScheduler.currentTime)
    }
    @Test fun pickupDuringTotalSilenceFeedbackCancelsActivation() = runTest {
        var activated = false
        val job = launch { ActivationFeedback.transition(true, { activated = true }, {}) }
        runCurrent()
        advanceTimeBy(500)
        job.cancel()
        advanceUntilIdle()
        assertFalse(activated)
    }

    @Test fun cancelledTransitionsDoNotPlayStaleFeedback() = runTest {
        var played = false
        val job = launch { ActivationFeedback.transition(false, {}, { played = true }) }
        runCurrent()
        job.cancel()
        advanceUntilIdle()
        assertFalse(played)
    }
}
