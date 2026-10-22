package dev.robin.flip_2_dnd.free

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.robin.flip_2_dnd.core.DetectionManager

class FreeDetectionManager(context: Context) : DetectionManager, SensorEventListener {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var callback: ((Boolean) -> Unit)? = null
    private var covered = false

    override fun isMediaPlaying() = false
    override fun areHeadphonesConnected() = false
    override fun isProximityCovered() = covered

    override fun registerProximityListener(callback: (Boolean) -> Unit) {
        unregisterProximityListener()
        this.callback = callback
        callback(false)
        proximity?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun unregisterProximityListener() {
        sensorManager.unregisterListener(this)
        callback = null
        covered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val nowCovered = event.values[0] < minOf(event.sensor.maximumRange, 5f)
        if (covered != nowCovered) {
            covered = nowCovered
            callback?.invoke(covered)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
