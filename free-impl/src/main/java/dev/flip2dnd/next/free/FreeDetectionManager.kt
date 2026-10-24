package dev.flip2dnd.next.free

import android.content.Context
import android.hardware.Sensor
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.flip2dnd.next.core.DetectionManager

class FreeDetectionManager(context: Context) : DetectionManager, SensorEventListener {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var callback: ((Boolean) -> Unit)? = null
    private var covered = false

    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    override fun isMediaPlaying() = audio.isMusicActive
    override fun areHeadphonesConnected(): Boolean = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        when (it.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_DEVICE -> true
            else -> (Build.VERSION.SDK_INT >= 26 && it.type == AudioDeviceInfo.TYPE_USB_HEADSET) ||
                Build.VERSION.SDK_INT >= 31 &&
                (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
    }
    override fun isProximityCovered() = covered

    override fun registerProximityListener(callback: (Boolean) -> Unit): Boolean {
        unregisterProximityListener()
        this.callback = callback
        callback(false)
        val success = try {
            proximity?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) } ?: false
        } catch (_: RuntimeException) { false }
        if (!success) unregisterProximityListener()
        return success
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
