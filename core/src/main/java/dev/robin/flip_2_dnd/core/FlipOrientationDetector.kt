package dev.robin.flip_2_dnd.core

import kotlin.math.sqrt

/** Hysteresis and dwell time keep table bumps and feedback from generating extra flips. */
class FlipOrientationDetector {
    var orientation = PhoneOrientation.UNKNOWN
        private set
    private var candidate = PhoneOrientation.UNKNOWN
    private var candidateSince = 0L

    fun reset() {
        orientation = PhoneOrientation.UNKNOWN
        candidate = PhoneOrientation.UNKNOWN
        candidateSince = 0L
    }

    fun update(x: Float, y: Float, z: Float, stable: Boolean, sensitivity: Float, nowMs: Long): PhoneOrientation {
        val magnitude = sqrt(x * x + y * y + z * z)
        if (!magnitude.isFinite() || magnitude < 1f) return orientation
        val faceDownFraction = -z / magnitude
        // Enter within roughly 20–30 degrees of flat; leave beyond roughly 40 degrees.
        val entryThreshold = 0.94f - 0.08f * sensitivity.coerceIn(0f, 1f)
        val next = when {
            orientation == PhoneOrientation.FACE_DOWN && faceDownFraction >= 0.75f -> PhoneOrientation.FACE_DOWN
            faceDownFraction >= entryThreshold -> if (stable) PhoneOrientation.FACE_DOWN else orientation
            else -> PhoneOrientation.FACE_UP
        }
        if (next != candidate) {
            candidate = next
            candidateSince = nowMs
        }
        val dwell = if (next == PhoneOrientation.FACE_DOWN) 300L else 150L
        if (nowMs - candidateSince >= dwell) orientation = next
        return orientation
    }
}
