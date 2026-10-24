package dev.flip2dnd.next.core

import kotlin.math.acos
import kotlin.math.roundToInt

/** The persisted slider position remains normalized; detection and UI share its angle mapping. */
object FlipTolerance {
    const val MIN_DEGREES = 5
    const val MAX_DEGREES = 30
    const val DEFAULT_POSITION = 0.84f // 26 degrees, close to the previous default.

    fun degrees(position: Float): Int =
        (MIN_DEGREES + (MAX_DEGREES - MIN_DEGREES) * position.coerceIn(0f, 1f)).roundToInt()

    fun position(degrees: Int): Float =
        (degrees.coerceIn(MIN_DEGREES, MAX_DEGREES) - MIN_DEGREES).toFloat() /
            (MAX_DEGREES - MIN_DEGREES)

    fun fromLegacySensitivity(sensitivity: Float): Float {
        val threshold = 0.94f - 0.08f * sensitivity.coerceIn(0f, 1f)
        return position(Math.toDegrees(acos(threshold.toDouble())).roundToInt())
    }
}
