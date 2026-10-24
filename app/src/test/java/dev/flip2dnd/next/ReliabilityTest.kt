package dev.flip2dnd.next

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import dev.flip2dnd.next.core.*
import dev.flip2dnd.next.data.repository.DndRepositoryImpl
import dev.flip2dnd.next.data.repository.SettingsRepositoryImpl
import dev.flip2dnd.next.services.DetectionControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ReliabilityTest {
    private lateinit var context: Application
    private lateinit var settings: SettingsRepositoryImpl
    private lateinit var audio: AudioManager
    private lateinit var notifications: NotificationManager
    private val repositories = mutableListOf<DndRepositoryImpl>()
    private val history = object : HistoryRepository {
        override fun getAllHistory() = flowOf(emptyList<HistoryItem>())
        override suspend fun addHistory(isEnabled: Boolean, dndMode: Int) {}
        override suspend fun clearHistory() {}
    }

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("flip_2_dnd_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("flip_dnd_session", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepositoryImpl(context)
        audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        DetectionControl.publish(false)
    }

    @After fun cleanup() {
        repositories.forEach { it.onCleared() }
        Dispatchers.resetMain()
    }

    private fun repository() = DndRepositoryImpl(context, settings, history).also { repositories += it }

    @Test fun switchingFromRingerToDndRestoresRingerBeforeClearingOwnership() = runTest {
        settings.setActivationMode(ActivationMode.RINGER)
        val repository = repository()
        runCurrent()
        repository.setActivated(true)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        settings.setActivationMode(ActivationMode.DND)
        runCurrent()
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertFalse(repository.isOwned().first())
    }

    @Test fun switchingFromDndToRingerRestoresDnd() = runTest {
        val repository = repository()
        runCurrent()
        repository.setActivated(true)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, notifications.currentInterruptionFilter)
        settings.setActivationMode(ActivationMode.RINGER)
        runCurrent()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, notifications.currentInterruptionFilter)
        assertFalse(repository.isOwned().first())
    }

    @Test fun manualRingerSelectionSurvivesRestoration() = runTest {
        settings.setActivationMode(ActivationMode.RINGER)
        val repository = repository()
        runCurrent()
        repository.setActivated(true)
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        repository.refresh()
        repository.setActivated(false)
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)
        assertFalse(repository.isOwned().first())
    }

    @Test fun sessionRecreationRestoresTheSavedRingerState() = runTest {
        settings.setActivationMode(ActivationMode.RINGER)
        val first = repository()
        runCurrent()
        first.setActivated(true)
        first.onCleared()
        repositories.remove(first)
        val restored = repository()
        runCurrent()
        assertTrue(restored.isOwned().first())
        restored.setActivated(false)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test fun unrelatedDndIsNeverOwnedOrTurnedOff() = runTest {
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        val repository = repository()
        runCurrent()
        repository.setActivated(true)
        repository.setActivated(false)
        assertFalse(repository.isOwned().first())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notifications.currentInterruptionFilter)
    }

    @Test fun settingsChangesAndActivityStartupPreservePause() = runTest {
        DetectionControl.pause(context)
        val application = shadowOf(context)
        // Drain the explicit pause request; settings and startup must enqueue nothing else.
        while (application.nextStartedService != null) { }
        settings.setSoundEnabled(true)
        settings.setVibrationEnabled(false)
        settings.setFlipSensitivity(0.8f)
        settings.setActivationMode(ActivationMode.RINGER)
        advanceUntilIdle()
        DetectionControl.startIfEnabled(context)
        assertFalse(DetectionControl.isEnabled(context))
        assertNull(application.nextStartedService)
    }

    @Test @Config(sdk = [23]) fun startupUsesCompatibleServiceApiOnAndroid6() {
        DetectionControl.resume(context)
        assertNotNull(shadowOf(context).nextStartedService)
        assertNull(DetectionControl.error.value)
    }
}
