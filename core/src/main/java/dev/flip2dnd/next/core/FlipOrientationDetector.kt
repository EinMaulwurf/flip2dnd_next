package dev.flip2dnd.next.core

import kotlin.math.sqrt
import kotlin.math.cos

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

    fun update(x: Float, y: Float, z: Float, stable: Boolean, sensitivity: Float, nowMs: Long, deactivationAngle: Int = 41): PhoneOrientation {
        val magnitude = sqrt(x * x + y * y + z * z)
        if (!magnitude.isFinite() || magnitude < 1f) return orientation
        val faceDownFraction = -z / magnitude
        // Keep a gap between entry and exit to avoid toggling at the boundary.
        val entryThreshold = cos(Math.toRadians(FlipTolerance.degrees(sensitivity).toDouble())).toFloat()
        val next = when {
            orientation == PhoneOrientation.FACE_DOWN && faceDownFraction >= cos(Math.toRadians(deactivationAngle.coerceIn(FlipTolerance.degrees(sensitivity) + 5, 90).toDouble())).toFloat() -> PhoneOrientation.FACE_DOWN
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
