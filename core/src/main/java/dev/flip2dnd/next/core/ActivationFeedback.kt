package dev.flip2dnd.next.core

import kotlinx.coroutines.delay

/** Keep a system DND/ringer transition from interrupting the app's waveform. */
object ActivationFeedback {
    suspend fun transition(
        activating: Boolean,
        applyState: suspend () -> Unit,
        playFeedback: suspend () -> Unit,
    ) {
        if (activating) {
            playFeedback()
            delay(2000) // Complete feedback before DND or a ringer change can suppress it.
        }
        applyState()
        if (!activating) {
            delay(250) // Allow system feedback and mode changes to settle first.
            playFeedback()
        }
    }
}
