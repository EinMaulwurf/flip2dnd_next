package dev.flip2dnd.next.data.repository

import android.app.NotificationManager
import android.media.AudioManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.flip2dnd.next.R
import dev.flip2dnd.next.core.DndRepository
import dev.flip2dnd.next.core.HistoryRepository
import dev.flip2dnd.next.core.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DndRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository
) : DndRepository {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isActivated = MutableStateFlow(false)
    private val _dndMode = MutableStateFlow(R.string.dnd_mode_all)

    private val stateMutex = Mutex()
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stateScope.launch { stateMutex.withLock { updateDndState() } }
        }
    }

    init {
        runBlocking {
            if (settingsRepository.getActivationMode().first() == dev.flip2dnd.next.core.ActivationMode.RINGER) {
                _isActivated.value = context.getSharedPreferences("flip_dnd_session", Context.MODE_PRIVATE)
                    .getBoolean("owned", false) && audioManager.ringerMode == settingsRepository.getRingerMode().first().value
            }
            stateMutex.withLock { updateDndState() }
        }
        val filter = IntentFilter().apply {
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        stateScope.launch {
            combine(settingsRepository.getActivationMode(), settingsRepository.getRingerMode()) { _, _ -> Unit }
                .collect { stateMutex.withLock { updateDndState() } }
        }
    }

    override fun isActivated(): Flow<Boolean> = _isActivated
    override fun getDndMode(): Flow<Int> = _dndMode

    override suspend fun setActivated(enabled: Boolean) = stateMutex.withLock {
        applyActivation(enabled)
    }

    private suspend fun applyActivation(enabled: Boolean) {
        val activationMode = settingsRepository.getActivationMode().first()
        if (activationMode == dev.flip2dnd.next.core.ActivationMode.DND) updateDndState()
        if (enabled == _isActivated.value) return
        val historyMode: Int
        if (activationMode == dev.flip2dnd.next.core.ActivationMode.DND) {
            check(notificationManager.isNotificationPolicyAccessGranted) {
                "Notification policy access is required to change DND"
            }
            historyMode = if (enabled) settingsRepository.getDndMode().first().filter
                else NotificationManager.INTERRUPTION_FILTER_ALL
            notificationManager.setInterruptionFilter(historyMode)
        } else {
            if (enabled) {
                settingsRepository.setPreviousRingerMode(audioManager.ringerMode)
                historyMode = settingsRepository.getRingerMode().first().value
            } else {
                historyMode = settingsRepository.getPreviousRingerMode().first()
            }
            audioManager.ringerMode = historyMode
        }
        // Publish only after the system call succeeds. Monitoring shares the same mutex,
        // so it cannot overwrite an in-flight change with an earlier system reading.
        _isActivated.value = enabled
        CoroutineScope(Dispatchers.IO).launch {
            historyRepository.addHistory(enabled, historyMode)
        }
    }

    override suspend fun toggle() = stateMutex.withLock {
        applyActivation(!_isActivated.value)
    }

    private suspend fun updateDndState() {
        val currentFilter = notificationManager.currentInterruptionFilter
        val isDndActive = currentFilter != NotificationManager.INTERRUPTION_FILTER_ALL

        val activationMode = settingsRepository.getActivationMode().first()

        val dndModeText = if (activationMode == dev.flip2dnd.next.core.ActivationMode.DND) {
            when (currentFilter) {
                NotificationManager.INTERRUPTION_FILTER_NONE -> R.string.dnd_mode_total_silence
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> R.string.dnd_mode_priority
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> R.string.dnd_mode_alarms_only
                NotificationManager.INTERRUPTION_FILTER_ALL -> R.string.dnd_mode_all
                else -> R.string.dnd_mode_unknown
            }
        } else {
            if (_isActivated.value) {
                val selectedRinger = settingsRepository.getRingerMode().first()
                when (selectedRinger) {
                    dev.flip2dnd.next.core.RingerMode.SILENT -> R.string.status_ringer_silent
                    dev.flip2dnd.next.core.RingerMode.VIBRATE -> R.string.status_ringer_vibrate
                    dev.flip2dnd.next.core.RingerMode.NORMAL -> R.string.dnd_mode_all
                }
            } else {
                R.string.dnd_mode_all
            }
        }

        if (_dndMode.value != dndModeText) {
            _dndMode.value = dndModeText
        }

        // Synchronization logic
        if (activationMode == dev.flip2dnd.next.core.ActivationMode.DND) {
            // In DND mode, we sync with system DND state
            if (isDndActive && !_isActivated.value) {
                _isActivated.value = true
            } else if (!isDndActive && _isActivated.value) {
                _isActivated.value = false
            }
        } else {
            // In RINGER mode, we don't sync with system DND state because
            // setting ringer to silent might trigger DND on some devices,
            // which would mess up our "isActivated" if we synced back.
            // We only record history when state changes via setActivated.
        }
    }

    // Ensure the monitoring job is cancelled when the repository is no longer used
    override fun onCleared() {
        context.unregisterReceiver(stateReceiver)
        stateScope.cancel()
    }
}
