package dev.robin.flip_2_dnd.services

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.withTimeout

/** Sensor wake-up delivery does not keep the CPU awake for coroutine delays. */
internal suspend fun <T> withTransitionWakeLock(
    context: Context,
    block: suspend () -> T,
): T {
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flip2DND::Transition")
        .apply { setReferenceCounted(false) }
    lock.acquire(30_000)
    return try {
        withTimeout(25_000) { block() }
    } finally {
        if (lock.isHeld) lock.release()
    }
}
