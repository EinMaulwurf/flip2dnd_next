package dev.flip2dnd.next.core

import kotlin.math.cos
import kotlin.math.sqrt

/** Uses sensor time so batched delivery cannot manufacture a quiet interval. */
class ActivationStability {
    data class State(val sinceMs: Long? = null, val ready: Boolean = false)
    var state = State()
        private set
    private var lastSampleMs: Long? = null

    fun reset() {
        state = State()
        lastSampleMs = null
    }

    fun update(x: Float, y: Float, z: Float, motion: Float, sensitivity: Float,
               tolerance: Float, requireStillness: Boolean, delayMs: Long, nowMs: Long): State {
        val previous = lastSampleMs
        if (previous != null && nowMs <= previous) return state
        if (previous != null && nowMs - previous > MAX_SAMPLE_GAP_MS) state = State()
        lastSampleMs = nowMs
        val magnitude = sqrt(x * x + y * y + z * z)
        val flat = magnitude.isFinite() && magnitude > 1f &&
            -z / magnitude >= cos(Math.toRadians(FlipTolerance.degrees(tolerance).toDouble()))
        // High sensitivity rejects smaller acceleration changes (m/s²).
        val threshold = 1.2f - sensitivity.coerceIn(0f, 1f) * 1.0f
        if (!flat || (requireStillness && (!motion.isFinite() || motion > threshold))) {
            state = State()
        } else {
            val since = state.sinceMs ?: nowMs
            val required = delayMs.coerceAtLeast(if (requireStillness) 1000L else 0L)
            state = State(since, nowMs - since >= required)
        }
        return state
    }

    fun isFresh(nowMs: Long): Boolean = lastSampleMs?.let { nowMs - it in 0..MAX_SAMPLE_GAP_MS } == true

    companion object { const val MAX_SAMPLE_GAP_MS = 1500L }
}
