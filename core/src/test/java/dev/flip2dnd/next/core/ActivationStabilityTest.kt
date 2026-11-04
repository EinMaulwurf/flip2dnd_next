package dev.flip2dnd.next.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ActivationStabilityTest {
    private val gate = ActivationStability()
    private fun sample(time: Long, motion: Float = 0f, angle: Double = 0.0,
                       stillness: Boolean = true, delay: Long = 2000, sensitivity: Float = 0.5f) =
        gate.update((9.81 * sin(Math.toRadians(angle))).toFloat(), 0f,
            (-9.81 * cos(Math.toRadians(angle))).toFloat(), motion, sensitivity,
            FlipTolerance.position(10), stillness, delay, time)

    @Test fun movementRestartsEntireQuietInterval() {
        sample(0); sample(1000)
        assertNull(sample(1500, motion = 2f).sinceMs)
        sample(1600); sample(2600)
        assertFalse(sample(3599).ready)
        assertTrue(sample(3600).ready)
    }

    @Test fun tiltOutsideActivationToleranceResetsEvenBelowExitAngle() {
        sample(0); sample(1000)
        assertNull(sample(1500, angle = 20.0, stillness = false).sinceMs)
        sample(1600); sample(2600)
        assertFalse(sample(3500).ready)
        assertTrue(sample(3600).ready)
    }

    @Test fun zeroDelayStillRequiresOneSecondWhenEnabled() {
        assertFalse(sample(0, delay = 0).ready)
        assertFalse(sample(999, delay = 0).ready)
        assertTrue(sample(1000, delay = 0).ready)
        gate.reset()
        assertTrue(sample(0, motion = 3f, stillness = false, delay = 0).ready)
    }

    @Test fun gapsAndRestartsCannotCountAsStillness() {
        sample(0)
        assertFalse(sample(5000).ready)
        assertEquals(5000L, gate.state.sinceMs)
        sample(6000); assertTrue(sample(7000).ready)
        assertFalse(gate.isFresh(9000))
        gate.reset()
        assertFalse(sample(9000).ready)
    }

    @Test fun sensitivityRejectsSmallerMovement() {
        assertNotNull(sample(0, motion = 0.5f, sensitivity = 0f).sinceMs)
        assertNull(sample(100, motion = 0.5f, sensitivity = 1f).sinceMs)
    }

    @Test fun invalidSamplesAndOutOfOrderDeliveryCannotConfirm() {
        sample(1000)
        sample(2000)
        assertFalse(sample(1500).ready)
        assertNull(sample(2100, motion = Float.NaN).sinceMs)
        assertNull(sample(2200, angle = Double.NaN).sinceMs)
    }
}
