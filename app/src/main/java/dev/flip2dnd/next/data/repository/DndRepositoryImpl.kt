package dev.flip2dnd.next.data.repository

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.flip2dnd.next.R
import dev.flip2dnd.next.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DndRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
) : DndRepository {
    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val preferences = context.getSharedPreferences("flip_dnd_session", Context.MODE_PRIVATE)
    private val dnd = DeviceDndController(context, notifications, preferences)
    private val sessions = ActivationSessionController(object : ActivationSessionController.Hardware {
        override fun read(mode: ActivationMode) = when (mode) {
            ActivationMode.DND -> dnd.read()
            ActivationMode.RINGER -> audio.ringerMode
        }
        override fun write(mode: ActivationMode, value: Int) {
            when (mode) {
                ActivationMode.DND -> dnd.write(value)
                ActivationMode.RINGER -> audio.ringerMode = value
            }
        }
    }, object : ActivationSessionController.Store {
        override fun load(): ActivationSession? {
            // Old boolean-only records cannot safely identify what needs restoring.
            val mode = preferences.getString("mode", null) ?: return null
            return runCatching {
                ActivationSession(ActivationMode.valueOf(mode), preferences.getInt("previous", 0),
                    preferences.getInt("applied", 0))
            }.getOrNull()
        }
        override fun save(session: ActivationSession?) {
            val edit = preferences.edit().remove("owned")
            if (session == null) edit.remove("mode").remove("previous").remove("applied")
            else edit.putString("mode", session.mode.name).putInt("previous", session.previous)
                .putInt("applied", session.applied)
            check(edit.commit()) { "Cannot persist activation session" }
        }
    })

    private val activated = MutableStateFlow(false)
    private val owned = MutableStateFlow(false)
    private val dndMode = MutableStateFlow(R.string.dnd_mode_all)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshInBackground() }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                addAction(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED)
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            settingsRepository.getActivationMode().collect { refreshSafely() }
        }
    }

    private fun refreshInBackground() { scope.launch { refreshSafely() } }
    private suspend fun refreshSafely() {
        try { refresh() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.e("DndRepository", "Cannot refresh device state", e) }
    }

    override fun isActivated(): Flow<Boolean> = activated
    override fun isOwned(): Flow<Boolean> = owned
    override fun getDndMode(): Flow<Int> = dndMode
    override suspend fun refresh() = mutex.withLock { updateState() }

    private suspend fun updateState() {
        sessions.reconcile()
        val mode = settingsRepository.getActivationMode().first()
        if (sessions.session?.mode?.let { it != mode } == true) sessions.restore()
        owned.value = sessions.session != null
        activated.value = if (mode == ActivationMode.DND) {
            notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } else owned.value
        dndMode.value = if (mode == ActivationMode.DND) {
            when (notifications.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_NONE -> R.string.dnd_mode_total_silence
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> R.string.dnd_mode_priority
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> R.string.dnd_mode_alarms_only
                else -> R.string.dnd_mode_all
            }
        } else if (!owned.value) R.string.dnd_mode_all else when (sessions.session?.applied) {
            AudioManager.RINGER_MODE_SILENT -> R.string.status_ringer_silent
            AudioManager.RINGER_MODE_VIBRATE -> R.string.status_ringer_vibrate
            else -> R.string.dnd_mode_all
        }
    }

    override suspend fun setActivated(enabled: Boolean) = mutex.withLock { applyActivation(enabled) }

    private suspend fun applyActivation(enabled: Boolean) {
        updateState()
        val changed: Boolean
        val historyMode: Int
        if (enabled) {
            // Existing DND belongs to the user or another rule. Do not take ownership.
            if (activated.value) return
            val mode = settingsRepository.getActivationMode().first()
            historyMode = if (mode == ActivationMode.DND) settingsRepository.getDndMode().first().filter
                else settingsRepository.getRingerMode().first().value
            changed = sessions.activate(mode, historyMode)
        } else {
            val previous = sessions.session?.previous
            if (previous == null) {
                // Complete the automatic condition even after a user's rule override
                // relinquished ownership; Android can then accept the next physical flip.
                dnd.endAutomaticCycle()
                return
            }
            historyMode = previous
            changed = sessions.restore()
        }
        updateState()
        if (changed) scope.launch(Dispatchers.IO) {
            try { historyRepository.addHistory(enabled, historyMode) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w("DndRepository", "Cannot save history", e) }
        }
    }

    override suspend fun toggle() = mutex.withLock {
        updateState()
        applyActivation(!activated.value)
    }

    override fun onCleared() {
        context.unregisterReceiver(receiver)
        scope.cancel()
    }
}
