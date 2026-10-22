package dev.robin.flip_2_dnd.core

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Shared by previews and DND feedback, with fine control near the quiet end. */
object VibrationFeedback {
    fun amplitude(strength: Float): Int {
        val level = strength.coerceIn(0f, 1f)
        return if (level == 0f) 0 else (255 * level * level).roundToInt().coerceIn(1, 255)
    }

    fun timings(pattern: LongArray, strength: Float): LongArray {
        val level = strength.coerceIn(0f, 1f)
        return pattern.mapIndexed { index, duration ->
            if (index % 2 == 0 || duration == 0L) duration
            else (duration * (0.05f + 0.95f * level)).roundToLong().coerceAtLeast(1L)
        }.toLongArray()
    }
}
