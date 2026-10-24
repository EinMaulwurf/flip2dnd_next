package dev.flip2dnd.next.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FlipOrientationDetectorTest {
    private val detector = FlipOrientationDetector()
    private fun sample(x: Float = 0f, z: Float = -9.81f, stable: Boolean = true, time: Long) =
        detector.update(x, 0f, z, stable, 0.5f, time)

    @Test fun activatesOnSlightlySlopedSurfaceAfterSettling() {
        assertEquals(PhoneOrientation.UNKNOWN, sample(x = 3f, z = -9.3f, time = 0))
        assertEquals(PhoneOrientation.FACE_DOWN, sample(x = 3f, z = -9.3f, time = 300))
    }

    @Test fun rotationMustSettleBeforeEnteringFaceDown() {
        sample(stable = false, time = 0)
        sample(stable = true, time = 200)
        assertEquals(PhoneOrientation.UNKNOWN, sample(time = 499))
        assertEquals(PhoneOrientation.FACE_DOWN, sample(time = 500))
    }

    @Test fun bumpsAndVibrationDoNotCreateAnotherFlip() {
        sample(time = 0)
        sample(time = 300)
        assertEquals(PhoneOrientation.FACE_DOWN, sample(x = 5f, z = -8f, stable = false, time = 400))
        assertEquals(PhoneOrientation.FACE_DOWN, sample(x = 5f, z = -8f, time = 1000))
        sample(x = 9f, z = -2f, time = 1100)
        assertEquals(PhoneOrientation.FACE_DOWN, sample(time = 1150))
    }

    @Test fun pickupRequiresBriefConfirmedExitAndCanActivateAgain() {
        sample(time = 0)
        sample(time = 300)
        sample(x = 9f, z = 0f, time = 500)
        assertEquals(PhoneOrientation.FACE_UP, sample(x = 9f, z = 0f, time = 650))
        sample(time = 800)
        assertEquals(PhoneOrientation.FACE_DOWN, sample(time = 1100))
    }

    @Test fun sensorRestartDoesNotReusePreviousFaceDownSample() {
        sample(time = 0)
        sample(time = 300)
        detector.reset()
        assertEquals(PhoneOrientation.UNKNOWN, detector.orientation)
        assertEquals(PhoneOrientation.UNKNOWN, sample(time = 1000))
    }

    @Test fun invalidReadingsDoNotChangeOrientation() {
        assertEquals(PhoneOrientation.UNKNOWN, sample(z = Float.NaN, time = 0))
        assertEquals(PhoneOrientation.UNKNOWN, sample(z = 0f, time = 300))
    }
}
