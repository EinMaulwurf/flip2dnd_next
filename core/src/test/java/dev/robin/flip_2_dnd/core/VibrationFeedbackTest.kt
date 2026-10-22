package dev.robin.flip_2_dnd.core

import org.junit.Assert.*
import org.junit.Test

class VibrationFeedbackTest {
    @Test fun quietEndHasFineControlAndZeroIsSilent() {
        assertEquals(0, VibrationFeedback.amplitude(0f))
        assertEquals(1, VibrationFeedback.amplitude(0.01f))
        assertEquals(3, VibrationFeedback.amplitude(0.1f))
        assertEquals(64, VibrationFeedback.amplitude(0.5f))
        assertEquals(255, VibrationFeedback.amplitude(1f))
    }

    @Test fun fullStrengthPreservesOriginalPattern() {
        val pattern = longArrayOf(0, 200, 100, 200, 400)
        assertArrayEquals(pattern, VibrationFeedback.timings(pattern, 1f))
    }

    @Test fun quietPulsesAreShorterWithoutChangingPausesOrInitialDelay() {
        assertArrayEquals(longArrayOf(0, 29, 100, 29, 400),
            VibrationFeedback.timings(longArrayOf(0, 200, 100, 200, 400), 0.1f))
    }

    @Test fun amplitudeControlPreservesRhythmAtLowStrength() {
        val pattern = longArrayOf(0, 200, 100, 200, 400)
        assertArrayEquals(pattern, VibrationFeedback.timings(pattern, 0.1f, amplitudeControl = true))
    }

    @Test fun outOfRangeStrengthIsClamped() {
        assertEquals(0, VibrationFeedback.amplitude(-1f))
        assertEquals(255, VibrationFeedback.amplitude(2f))
        assertArrayEquals(longArrayOf(), VibrationFeedback.timings(longArrayOf(), 0.5f))
    }
}
