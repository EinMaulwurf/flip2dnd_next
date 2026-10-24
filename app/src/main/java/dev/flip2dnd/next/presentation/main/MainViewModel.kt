package dev.flip2dnd.next.presentation.main

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.flip2dnd.next.R
import dev.flip2dnd.next.core.PhoneOrientation
import dev.flip2dnd.next.core.DndRepository
import dev.flip2dnd.next.core.FeedbackRepository
import dev.flip2dnd.next.core.ScreenStateRepository
import dev.flip2dnd.next.core.SettingsRepository
import dev.flip2dnd.next.domain.usecase.GetOrientationUseCase
import dev.flip2dnd.next.domain.usecase.GetSettingsUseCase
import dev.flip2dnd.next.domain.usecase.ToggleDndUseCase
import dev.flip2dnd.next.services.DetectionControl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getOrientationUseCase: GetOrientationUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val toggleDndUseCase: ToggleDndUseCase,
    private val dndRepository: DndRepository,
    private val settingsRepository: SettingsRepository,
    private val feedbackRepository: FeedbackRepository,
    private val screenStateRepository: ScreenStateRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    init {
        observeOrientation()
        observeSettings()
        observeDndState()
        observeScreenState()
        screenStateRepository.startMonitoring()
        updateServiceState()
    }

    private fun updateServiceState() {
        viewModelScope.launch {
            combine(DetectionControl.running, DetectionControl.error) { running, error ->
                _state.update { it.copy(isServiceRunning = running, detectionError = error) }
            }.collect()
        }
    }

    fun toggleService() {
        if (DetectionControl.running.value) DetectionControl.pause(context)
        else DetectionControl.resume(context)
    }

    private fun observeScreenState() {
        viewModelScope.launch {
            screenStateRepository.isScreenOff().collect { screenOff ->
                Log.d("MainViewModel", "Screen state changed: ${if (screenOff) "OFF" else "ON"}")
            }
        }
    }

    private fun observeOrientation() {
        viewModelScope.launch {
            screenStateRepository.isScreenOff().collectLatest { screenOff ->
                if (!screenOff) {
                    getOrientationUseCase().collect { orientation ->
                        _state.update { it.copy(orientation = orientation) }
                    }
                }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            getSettingsUseCase.getScreenOffOnlyEnabled().collect { enabled ->
                _state.update { it.copy(isScreenOffOnly = enabled) }
            }
        }
        viewModelScope.launch {
            getSettingsUseCase.getVibrationEnabled().collect { enabled ->
                _state.update { it.copy(isVibrationEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            getSettingsUseCase.getSoundEnabled().collect { enabled ->
                _state.update { it.copy(isSoundEnabled = enabled) }
            }
        }
    }

    private fun observeDndState() {
        viewModelScope.launch {
            // Combine DND enabled state, activation mode, and specific modes
            combine(
                dndRepository.isActivated(),
                settingsRepository.getActivationMode(),
                settingsRepository.getDndMode(),
                settingsRepository.getRingerMode()
            ) { isActivated, activationMode, dndMode, ringerMode ->
                val modeResId = if (activationMode == dev.flip2dnd.next.core.ActivationMode.DND) {
                    when (dndMode) {
                        dev.flip2dnd.next.core.DndMode.PRIORITY -> R.string.dnd_mode_priority
                        dev.flip2dnd.next.core.DndMode.TOTAL_SILENCE -> R.string.dnd_mode_total_silence
                        dev.flip2dnd.next.core.DndMode.ALARMS_ONLY -> R.string.dnd_mode_alarms_only
                    }
                } else {
                    when (ringerMode) {
                        dev.flip2dnd.next.core.RingerMode.SILENT -> R.string.status_ringer_silent
                        dev.flip2dnd.next.core.RingerMode.VIBRATE -> R.string.status_ringer_vibrate
                        dev.flip2dnd.next.core.RingerMode.NORMAL -> R.string.dnd_mode_all
                    }
                }

                _state.update {
                    it.copy(
                        isDndEnabled = isActivated,
                        dndMode = modeResId
                    )
                }
            }.collect()
        }
    }

    override fun onCleared() {
        super.onCleared()
        screenStateRepository.stopMonitoring()
    }
}
