package dev.flip2dnd.next.free

import android.content.Context
import dev.flip2dnd.next.core.SensorManagerPro

class FreeSensorManagerPro(context: Context) : SensorManagerPro {
    override fun getOrientationThresholds(sensitivity: Float): SensorManagerPro.Thresholds {
        val gyro = 0.10f + ((1.0f - sensitivity) * 0.05f)
        val accel = 9.0f + ((1.0f - sensitivity) * 0.75f)
        return SensorManagerPro.Thresholds(gyro, accel)
    }
}
