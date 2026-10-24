package dev.flip2dnd.next.services

import android.app.NotificationManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dev.flip2dnd.next.core.SettingsRepository
import dev.flip2dnd.next.core.DndRepository
import dev.flip2dnd.next.core.VibrationPattern
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "DndService"

class DndService(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dndRepository: DndRepository
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private val feedbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var feedbackJob: Job? = null

    private val soundService = SoundService(context, settingsRepository)

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraId = try {
        cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) {
        null
    }

    private val _isDndEnabled = MutableStateFlow(false)
    val isDndEnabled: StateFlow<Boolean> = _isDndEnabled

    private val _isAppEnabledDnd = MutableStateFlow(false)
    val isAppEnabledDnd: StateFlow<Boolean> = _isAppEnabledDnd

    private val _flashlightOn = MutableStateFlow(false)
    val flashlightOn = _flashlightOn.asStateFlow()
    val isFlashlightOn: Boolean get() = _flashlightOn.value

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            if (cameraId == this@DndService.cameraId) {
                _flashlightOn.value = enabled
                Log.d(TAG, "Flashlight state changed: $isFlashlightOn")
            }
        }
    }

    init {
        feedbackScope.launch { updateDndStatus() }
        try {
            cameraManager.registerTorchCallback(torchCallback, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering torch callback: ${e.message}")
        }
    }

    fun cleanup() {
        feedbackScope.cancel()
        vibrator.cancel()
        soundService.release()
        try {
            cameraManager.unregisterTorchCallback(torchCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering torch callback: ${e.message}")
        }
    }

    private suspend fun vibrate(pattern: LongArray) {
        run {
            val isVibrationEnabled = settingsRepository.getVibrationEnabled().first()
            Log.d(
                TAG,
                "Vibration check: enabled=$isVibrationEnabled, pattern=${pattern.contentToString()}"
            )

            if (!isVibrationEnabled) {
                Log.w(TAG, "Vibration is disabled. Skipping vibration.")
                return@run
            }

            // Check vibration schedule
            val scheduleEnabled = settingsRepository.getVibrationScheduleEnabled().first()
            if (scheduleEnabled) {
                val startTime = settingsRepository.getVibrationScheduleStartTime().first()
                val endTime = settingsRepository.getVibrationScheduleEndTime().first()
                val days = settingsRepository.getVibrationScheduleDays().first()
                if (!isWithinSchedule(startTime, endTime, days)) {
                    Log.d(TAG, "Current time is outside vibration schedule. Skipping vibration.")
                    return@run
                }
            }

            try {
                val useCustomVibration = settingsRepository.getUseCustomVibration().first()
                val customStrength = if (useCustomVibration) {
                    settingsRepository.getCustomVibrationStrength().first()
                } else {
                    1.0f
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val baseAmplitude = dev.flip2dnd.next.core.VibrationFeedback.amplitude(customStrength)
                    if (baseAmplitude == 0 || pattern.isEmpty()) return@run
                    val amplitudes = IntArray(pattern.size) { index ->
                        if (index % 2 == 0) 0 else if (vibrator.hasAmplitudeControl()) baseAmplitude else android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    }
                    val adjustedPattern = dev.flip2dnd.next.core.VibrationFeedback.timings(pattern, customStrength, vibrator.hasAmplitudeControl())
                    vibrator.vibrate(VibrationEffect.createWaveform(adjustedPattern, amplitudes, -1))
                } else {
                    Log.d(TAG, "Using deprecated vibration method")
                    @Suppress("DEPRECATION")
                    if (customStrength > 0f && pattern.isNotEmpty()) {
                        vibrator.vibrate(dev.flip2dnd.next.core.VibrationFeedback.timings(pattern, customStrength), -1)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error during vibration: ${e.message}", e)
            }
        }
    }

    private suspend fun playSound(isEnabled: Boolean) {
        run {
            val scheduleEnabled = settingsRepository.getSoundScheduleEnabled().first()
            if (scheduleEnabled) {
                val startTime = settingsRepository.getSoundScheduleStartTime().first()
                val endTime = settingsRepository.getSoundScheduleEndTime().first()
                val days = settingsRepository.getSoundScheduleDays().first()
                if (!isWithinSchedule(startTime, endTime, days)) {
                    Log.d(TAG, "Current time is outside sound schedule. Skipping sound.")
                    return@run
                }
            }
            soundService.playDndSound(isEnabled)
        }
    }

    suspend fun setActivated(enabled: Boolean): Boolean {
        updateDndStatus()
        if (enabled && _isDndEnabled.value || !enabled && !_isAppEnabledDnd.value) return false
        try {
            feedbackJob?.cancel()
            vibrator.cancel()
            if (settingsRepository.getActivationMode().first() == dev.flip2dnd.next.core.ActivationMode.DND &&
                !notificationManager.isNotificationPolicyAccessGranted) return false
            val totalSilence = enabled &&
                settingsRepository.getActivationMode().first() == dev.flip2dnd.next.core.ActivationMode.DND &&
                settingsRepository.getDndMode().first() == dev.flip2dnd.next.core.DndMode.TOTAL_SILENCE

            suspend fun feedback() {
                playSound(enabled)
                val pattern = if (enabled) settingsRepository.getDndOnVibration().first()
                    else settingsRepository.getDndOffVibration().first()
                if (pattern != VibrationPattern.NONE) vibrate(pattern.pattern)
            }

            suspend fun applyState() {
                dndRepository.setActivated(enabled)
                updateDndStatus()
            }
            if (totalSilence) {
                dev.flip2dnd.next.core.ActivationFeedback.transition(true, { applyState() }, { feedback() })
            } else {
                applyState()
                // This job belongs to the transition, not the sensor event that requested it.
                feedbackJob = feedbackScope.launch {
                    try {
                        withTransitionWakeLock(context) {
                            dev.flip2dnd.next.core.ActivationFeedback.transition(false, {}, { feedback() })
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error playing activation feedback", e)
                    }
                }
            }
            return if (enabled) _isAppEnabledDnd.value else !_isAppEnabledDnd.value
        } catch (e: CancellationException) {
            vibrator.cancel()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error setting activation state", e)
            return false
        }
    }

    suspend fun updateDndStatus() {
        try {
            dndRepository.refresh()
            _isDndEnabled.value = dndRepository.isActivated().first()
            _isAppEnabledDnd.value = dndRepository.isOwned().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status: ${e.message}", e)
        }
    }

    private fun isWithinSchedule(startTime: String, endTime: String, days: Set<Int>): Boolean {
        return dev.flip2dnd.next.core.ServiceLocator.getScheduleManager(context)
            .isWithinSchedule(startTime, endTime, days)
    }
}
