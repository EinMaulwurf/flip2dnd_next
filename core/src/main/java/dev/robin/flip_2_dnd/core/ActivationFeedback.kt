package dev.robin.flip_2_dnd.core

import kotlinx.coroutines.delay

/** Keep a system DND/ringer transition from interrupting the app's waveform. */
object ActivationFeedback {
    suspend fun transition(
        totalSilence: Boolean,
        applyState: suspend () -> Unit,
        playFeedback: suspend () -> Unit,
    ) {
        if (totalSilence) {
            playFeedback()
            delay(2000) // Complete feedback before Total Silence suppresses it.
        }
        applyState()
        if (!totalSilence) {
            delay(250) // Allow system feedback and mode changes to settle first.
            playFeedback()
        }
    }
}
