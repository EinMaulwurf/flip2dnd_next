package dev.robin.flip_2_dnd.services

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import dev.robin.flip_2_dnd.R
import dev.robin.flip_2_dnd.core.SettingsRepository
import dev.robin.flip_2_dnd.core.DndRepository
import dev.robin.flip_2_dnd.core.FlashlightPattern
import dev.robin.flip_2_dnd.core.VibrationPattern
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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

	var isFlashlightOn = false
		private set

	private val torchCallback = object : CameraManager.TorchCallback() {
		override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
			super.onTorchModeChanged(cameraId, enabled)
			if (cameraId == this@DndService.cameraId) {
				isFlashlightOn = enabled
				Log.d(TAG, "Flashlight state changed: $isFlashlightOn")
			}
		}
	}

	init {
		// Update local state flows for UI sync
		updateDndStatus()
		try {
			cameraManager.registerTorchCallback(torchCallback, null)
		} catch (e: Exception) {
			Log.e(TAG, "Error registering torch callback: ${e.message}")
		}
	}

	fun cleanup() {
		try {
			cameraManager.unregisterTorchCallback(torchCallback)
		} catch (e: Exception) {
			Log.e(TAG, "Error unregistering torch callback: ${e.message}")
		}
	}

	private fun vibrate(pattern: LongArray) {
		runBlocking {
			val isVibrationEnabled = settingsRepository.getVibrationEnabled().first()
			Log.d(
				TAG,
				"Vibration check: enabled=$isVibrationEnabled, pattern=${pattern.contentToString()}"
			)

			if (!isVibrationEnabled) {
				Log.w(TAG, "Vibration is disabled. Skipping vibration.")
				return@runBlocking
			}

			// Check vibration schedule
			val scheduleEnabled = settingsRepository.getVibrationScheduleEnabled().first()
			if (scheduleEnabled) {
				val startTime = settingsRepository.getVibrationScheduleStartTime().first()
				val endTime = settingsRepository.getVibrationScheduleEndTime().first()
				val days = settingsRepository.getVibrationScheduleDays().first()
				if (!isWithinSchedule(startTime, endTime, days)) {
					Log.d(TAG, "Current time is outside vibration schedule. Skipping vibration.")
					return@runBlocking
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
					val baseAmplitude = dev.robin.flip_2_dnd.core.VibrationFeedback.amplitude(customStrength)
					if (baseAmplitude == 0 || pattern.isEmpty()) return@runBlocking
					val amplitudes = IntArray(pattern.size) { index ->
						if (index % 2 == 0) 0 else baseAmplitude
					}
					val adjustedPattern = dev.robin.flip_2_dnd.core.VibrationFeedback.timings(pattern, customStrength)
					vibrator.vibrate(VibrationEffect.createWaveform(adjustedPattern, amplitudes, -1))
				} else {
					Log.d(TAG, "Using deprecated vibration method")
					@Suppress("DEPRECATION")
					if (customStrength > 0f && pattern.isNotEmpty()) {
						vibrator.vibrate(dev.robin.flip_2_dnd.core.VibrationFeedback.timings(pattern, customStrength), -1)
					}
				}
			} catch (e: Exception) {
				Log.e(TAG, "Error during vibration: ${e.message}", e)
			}
		}
	}

    private fun playSound(isEnabled: Boolean) {
        runBlocking {
            val scheduleEnabled = settingsRepository.getSoundScheduleEnabled().first()
            if (scheduleEnabled) {
                val startTime = settingsRepository.getSoundScheduleStartTime().first()
                val endTime = settingsRepository.getSoundScheduleEndTime().first()
                val days = settingsRepository.getSoundScheduleDays().first()
                if (!isWithinSchedule(startTime, endTime, days)) {
                    Log.d(TAG, "Current time is outside sound schedule. Skipping sound.")
                    return@runBlocking
                }
            }
            soundService.playDndSound(isEnabled)
        }
    }

    private fun checkDndPermission(): Boolean {
        val hasPermission = notificationManager.isNotificationPolicyAccessGranted
        Log.d(TAG, "DND Permission check: $hasPermission")
        return hasPermission
    }

    private fun openDndSettings() {
        Log.d(TAG, "Opening DND settings")
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun toggleDnd() {
        try {
            val willBeActivated = !dndRepository.isActivated().first()
            val totalSilence = willBeActivated &&
                settingsRepository.getActivationMode().first() == dev.robin.flip_2_dnd.core.ActivationMode.DND &&
                settingsRepository.getDndMode().first() == dev.robin.flip_2_dnd.core.DndMode.TOTAL_SILENCE

            fun feedback() {
                playSound(willBeActivated)
                val pattern = runBlocking {
                    if (willBeActivated) settingsRepository.getDndOnVibration().first()
                    else settingsRepository.getDndOffVibration().first()
                }
                if (pattern != VibrationPattern.NONE) vibrate(pattern.pattern)
            }

            dev.robin.flip_2_dnd.core.ActivationFeedback.transition(
                totalSilence = totalSilence,
                applyState = {
                    dndRepository.setActivated(willBeActivated)
                    _isDndEnabled.value = willBeActivated
                    _isAppEnabledDnd.value = willBeActivated
                },
                playFeedback = { feedback() },
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling activation state", e)
        }
    }

    fun updateDndStatus() {
        try {
            val activationMode = runBlocking { settingsRepository.getActivationMode().first() }
            val isActivated = runBlocking { dndRepository.isActivated().first() }
            
            if (activationMode == dev.robin.flip_2_dnd.core.ActivationMode.DND) {
                val currentFilter = notificationManager.currentInterruptionFilter
                val isDndOn = currentFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                _isDndEnabled.value = isDndOn
                if (!isDndOn) {
                    _isAppEnabledDnd.value = false
                }
            } else {
                // In Ringer mode, we rely on the repository's activation state
                _isDndEnabled.value = isActivated
                if (!isActivated) {
                    _isAppEnabledDnd.value = false
                }
            }
            
            Log.d(
                TAG,
                "Updated status: isActivated=$isActivated, mode=$activationMode, isDndEnabled=${_isDndEnabled.value}, isAppEnabled=${_isAppEnabledDnd.value}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status: ${e.message}", e)
        }
    }

	private fun flashFlashlight(pattern: FlashlightPattern) {
		if (cameraId == null || pattern == FlashlightPattern.NONE) return

		runBlocking {
			val isFlashlightEnabled = settingsRepository.getFlashlightFeedbackEnabled().first()
			if (!isFlashlightEnabled) return@runBlocking

			// Check if Pro feature
			val context = this@DndService.context
			if (!dev.robin.flip_2_dnd.core.ServiceLocator.getFeatureManager(context).flashlightFeedbackEnabled()) return@runBlocking

			val feedbackWithFlashlightOn = settingsRepository.getFeedbackWithFlashlightOn().first()
			val flashController = dev.robin.flip_2_dnd.core.ServiceLocator.getFlashController(context)

			if (flashController.shouldSkipFeedback(isFlashlightOn, feedbackWithFlashlightOn)) {
				return@runBlocking
			}

			// Check schedule
			val scheduleEnabled = settingsRepository.getFlashlightScheduleEnabled().first()
			if (scheduleEnabled) {
				val startTime = settingsRepository.getFlashlightScheduleStartTime().first()
				val endTime = settingsRepository.getFlashlightScheduleEndTime().first()
				val days = settingsRepository.getFlashlightScheduleDays().first()
				val scheduleManager = dev.robin.flip_2_dnd.core.ServiceLocator.getScheduleManager(context)
				if (!scheduleManager.isWithinSchedule(startTime, endTime, days)) {
					Log.d(TAG, "Current time is outside flashlight schedule. Skipping flashlight blink.")
					return@runBlocking
				}
			}

			val flashlightIntensity = settingsRepository.getFlashlightIntensity().first()
			flashController.flashFlashlight(pattern.pattern, flashlightIntensity)
		}
	}

	private fun isWithinSchedule(startTime: String, endTime: String, days: Set<Int>): Boolean {
		return dev.robin.flip_2_dnd.core.ServiceLocator.getScheduleManager(context)
			.isWithinSchedule(startTime, endTime, days)
	}
}
