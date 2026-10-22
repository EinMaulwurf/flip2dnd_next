package dev.robin.flip_2_dnd.free

import android.content.Context
import dev.robin.flip_2_dnd.core.SensorManagerPro

class FreeSensorManagerPro(context: Context) : SensorManagerPro {
    override fun getOrientationThresholds(sensitivity: Float): SensorManagerPro.Thresholds {
        val gyro = 0.10f + ((1.0f - sensitivity) * 0.05f)
        val accel = 9.0f + ((1.0f - sensitivity) * 0.75f)
        return SensorManagerPro.Thresholds(gyro, accel)
    }
}
