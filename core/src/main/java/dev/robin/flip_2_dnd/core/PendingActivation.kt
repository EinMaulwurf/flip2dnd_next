package dev.robin.flip_2_dnd.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One continuous eligible face-down interval gets one delay, regardless of event count. */
class PendingActivation(
    private val scope: CoroutineScope,
    private val runWhileAwake: suspend (suspend () -> Unit) -> Unit = { it() },
) {
    private var job: Job? = null

    fun startIfIdle(delayMs: Long, onStarted: () -> Unit = {}, activate: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch {
            runWhileAwake {
                onStarted()
                delay(delayMs.coerceAtLeast(0))
                activate()
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
