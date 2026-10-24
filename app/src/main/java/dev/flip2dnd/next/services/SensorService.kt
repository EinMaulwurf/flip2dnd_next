package dev.flip2dnd.next.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import dev.flip2dnd.next.diagnostics.DiagnosticLog
import dev.flip2dnd.next.core.FlipOrientationDetector
import dev.flip2dnd.next.core.PhoneOrientation
import dev.flip2dnd.next.core.SensorMonitoringSession
import dev.flip2dnd.next.core.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

private const val TAG = "SensorService"

class SensorService(context: Context, settingsRepository: SettingsRepository) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val wakeAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
    // Tilt is an Android-defined special-trigger sensor, exposed by string type on some
    // devices. No hidden APIs or vendor-specific sensor numbers are needed to use it.
    private val tilt = sensorManager.getSensorList(Sensor.TYPE_ALL).firstOrNull {
        it.stringType == "android.sensor.tilt_detector" && it.isWakeUpSensor &&
            it.reportingMode == Sensor.REPORTING_MODE_SPECIAL_TRIGGER
    }
    private val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flip2DNDNext::Sensors")
        .apply { setReferenceCounted(false) }
    private val deliveryWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flip2DNDNext::SensorDelivery")
        .apply { setReferenceCounted(false) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detector = FlipOrientationDetector()
    private val filtered = FloatArray(3)
    private var previousTimestamp = 0L
    private var registeredAtNs = 0L
    private var registeredAcceleration: Sensor? = null
    private var sensitivity = dev.flip2dnd.next.core.FlipTolerance.DEFAULT_POSITION
    private val _orientation = MutableStateFlow(PhoneOrientation.UNKNOWN)
    val orientation = _orientation.asStateFlow()

    private val listener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor == tilt) {
                DiagnosticLog.event("tilt_delivered", "delivery_delay_ms" to
                    (SystemClock.elapsedRealtime() - event.timestamp / 1_000_000L).coerceAtLeast(0))
                session.onTilt()
                return
            }
            if (event.sensor != registeredAcceleration || event.values.size < 3 ||
                event.timestamp < registeredAtNs) return
            // Sensor time, rather than delivery time, is essential when a batch arrives.
            val now = event.timestamp / 1_000_000L
            if (previousTimestamp != 0L && now <= previousTimestamp) return
            val dt = (now - previousTimestamp).coerceAtLeast(0)
            val alpha = if (previousTimestamp == 0L) 1f else (1 - exp(-dt / 120.0)).toFloat()
            previousTimestamp = now
            var differenceSquared = 0f
            var magnitudeSquared = 0f
            for (i in 0..2) {
                filtered[i] += alpha * (event.values[i] - filtered[i])
                val difference = event.values[i] - filtered[i]
                differenceSquared += difference * difference
                magnitudeSquared += event.values[i] * event.values[i]
            }
            // Acceleration and direction must settle; a continuously running gyro is unnecessary.
            val stable = abs(sqrt(magnitudeSquared) - SensorManager.GRAVITY_EARTH) < 1.5f &&
                differenceSquared < 1.5f
            val next = detector.update(filtered[0], filtered[1], filtered[2], stable, sensitivity, now)
            if (next != _orientation.value) {
                // Hand the wake-up event to the coroutine that owns the longer transition lease.
                if (!powerManager.isInteractive) deliveryWakeLock.acquire(2000)
                DiagnosticLog.event("orientation_confirmed", "orientation" to next.name,
                    "delivery_delay_ms" to (SystemClock.elapsedRealtime() - now).coerceAtLeast(0),
                    "screen_off" to !powerManager.isInteractive, "monitoring_mode" to monitoringMode.value.name)
                _orientation.value = next
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val session: SensorMonitoringSession = SensorMonitoringSession(scope, object : SensorMonitoringSession.Hardware {
        override fun armTilt(): Boolean = register(tilt, SensorManager.SENSOR_DELAY_NORMAL, 0)
        override fun disarmTilt() { tilt?.let { sensorManager.unregisterListener(listener, it) } }

        override fun startAcceleration(wakeUp: Boolean, batch: Boolean): Boolean {
            val sensor = if (wakeUp) wakeAccelerometer else accelerometer
            if (sensor == null) return false
            if (registeredAcceleration == sensor) return true
            stopAcceleration()
            previousTimestamp = 0L
            registeredAtNs = SystemClock.elapsedRealtimeNanos()
            filtered.fill(0f)
            val success = register(sensor, if (batch) 500_000 else SensorManager.SENSOR_DELAY_NORMAL,
                if (batch) 1_000_000 else 0)
            if (success) registeredAcceleration = sensor
            return success
        }

        override fun stopAcceleration() {
            registeredAcceleration?.let {
                sensorManager.unregisterListener(listener, it)
                DiagnosticLog.event("accelerometer_stopped", "wake_up" to it.isWakeUpSensor)
            }
            registeredAcceleration = null
        }

        override fun acquireWakeLock(timeoutMs: Long) {
            wakeLock.acquire(timeoutMs)
            DiagnosticLog.event("sensor_wake_lock_acquired", "timeout_ms" to timeoutMs)
        }
        override fun releaseWakeLock() {
            if (wakeLock.isHeld) {
                wakeLock.release()
                DiagnosticLog.event("sensor_wake_lock_released")
            }
        }
    })
    val monitoringMode = session.mode
    // A tilt listener is still monitoring even when its accelerometer snapshot has ended.
    val isRegistered: Boolean get() = monitoringMode.value != SensorMonitoringSession.Mode.STOPPED &&
        monitoringMode.value != SensorMonitoringSession.Mode.UNAVAILABLE

    init {
        scope.launch { monitoringMode.collect { DiagnosticLog.modeChanged(it.name) } }
        scope.launch { settingsRepository.getFlipSensitivity().collect { sensitivity = it.coerceIn(0f, 1f) } }
    }

    private fun register(sensor: Sensor?, periodUs: Int, latencyUs: Int): Boolean {
        if (sensor == null) return false
        return try {
            sensorManager.registerListener(listener, sensor, periodUs, latencyUs).also { success ->
                DiagnosticLog.event("sensor_registration", "type" to sensor.stringType,
                    "wake_up" to sensor.isWakeUpSensor, "period_us" to periodUs,
                    "latency_us" to latencyUs, "success" to success)
            }
        } catch (e: RuntimeException) {
            DiagnosticLog.failure("sensor_registration_failed", e)
            Log.w(TAG, "Cannot register ${sensor.name}; trying another monitoring strategy", e)
            false
        }
    }

    fun startMonitoring() {
        if (!isRegistered) {
            detector.reset()
            _orientation.value = PhoneOrientation.UNKNOWN
        }
        session.start(screenOff = !powerManager.isInteractive)
    }

    suspend fun <T> confirm(block: suspend () -> T): T = session.confirm(block)

    fun setScreenOff(screenOff: Boolean) = session.setScreenOff(screenOff)

    fun stopMonitoring() {
        session.stop()
        DiagnosticLog.modeChanged(SensorMonitoringSession.Mode.STOPPED.name)
        if (deliveryWakeLock.isHeld) deliveryWakeLock.release()
        detector.reset()
        _orientation.value = PhoneOrientation.UNKNOWN
    }

    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}
