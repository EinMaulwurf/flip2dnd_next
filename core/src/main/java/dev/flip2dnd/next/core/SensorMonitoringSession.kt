package dev.flip2dnd.next.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns sensor registrations and their wake lock across screen changes and shutdown. */
class SensorMonitoringSession(
    private val scope: CoroutineScope,
    private val hardware: Hardware,
) {
    interface Hardware {
        fun armTilt(): Boolean
        fun disarmTilt()
        fun startAcceleration(wakeUp: Boolean, batch: Boolean): Boolean
        fun stopAcceleration()
        fun acquireWakeLock(timeoutMs: Long)
        fun releaseWakeLock()
    }

    enum class Mode { STOPPED, SCREEN_ON, TILT, WAKE_UP_ACCELEROMETER, LEGACY }
    private val _mode = MutableStateFlow(Mode.STOPPED)
    val mode = _mode.asStateFlow()
    private var enabled = false
    private var screenOff = false
    private var samplingJob: Job? = null
    private var confirmations = 0

    fun start(screenOff: Boolean) {
        if (enabled && this.screenOff == screenOff && _mode.value != Mode.STOPPED) return
        enabled = true
        this.screenOff = screenOff
        configure()
    }

    fun setScreenOff(screenOff: Boolean) {
        if (this.screenOff == screenOff) return
        this.screenOff = screenOff
        if (enabled) configure()
    }

    private fun configure() {
        releaseHardware()
        if (!screenOff) {
            if (hardware.startAcceleration(wakeUp = false, batch = false)) _mode.value = Mode.SCREEN_ON
            return
        }
        // Arm before the initial snapshot, so there is no blind period when sampling ends.
        if (hardware.armTilt()) {
            _mode.value = Mode.TILT
            onTilt()
        } else {
            startFallback()
        }
    }

    private fun startFallback() {
        if (hardware.startAcceleration(wakeUp = true, batch = true)) {
            _mode.value = Mode.WAKE_UP_ACCELEROMETER
        } else if (hardware.startAcceleration(wakeUp = false, batch = false)) {
            _mode.value = Mode.LEGACY
            // Older hardware cannot wake on a flip. Keep reliability, and report this
            // higher-power fallback rather than silently dropping screen-off events.
            hardware.acquireWakeLock(LEGACY_LEASE_MS)
            samplingJob = scope.launch {
                while (isActive) {
                    delay(LEGACY_LEASE_MS / 2)
                    hardware.acquireWakeLock(LEGACY_LEASE_MS)
                }
            }
        }
    }

    fun onTilt() {
        if (!enabled || _mode.value != Mode.TILT) return
        samplingJob?.cancel()
        hardware.acquireWakeLock(if (confirmations > 0) 30_000 else SNAPSHOT_MS + 1000)
        if (!hardware.startAcceleration(wakeUp = false, batch = false)) {
            hardware.disarmTilt()
            hardware.releaseWakeLock()
            _mode.value = Mode.STOPPED
            startFallback()
            return
        }
        if (confirmations > 0) return
        samplingJob = scope.launch {
            delay(SNAPSHOT_MS)
            hardware.stopAcceleration()
            hardware.releaseWakeLock()
        }
    }

    suspend fun <T> confirm(block: suspend () -> T): T {
        confirmations++
        onTilt()
        return try {
            block()
        } finally {
            confirmations--
            onTilt()
        }
    }

    fun stop() {
        enabled = false
        releaseHardware()
    }

    private fun releaseHardware() {
        samplingJob?.cancel()
        samplingJob = null
        hardware.stopAcceleration()
        hardware.disarmTilt()
        hardware.releaseWakeLock()
        _mode.value = Mode.STOPPED
    }

    companion object {
        const val SNAPSHOT_MS = 4000L
        private const val LEGACY_LEASE_MS = 120_000L
    }
}
