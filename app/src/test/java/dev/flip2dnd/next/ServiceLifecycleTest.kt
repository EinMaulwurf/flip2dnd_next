package dev.flip2dnd.next

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import dev.flip2dnd.next.core.ActivationMode
import dev.flip2dnd.next.data.repository.SettingsRepositoryImpl
import dev.flip2dnd.next.services.DetectionControl
import dev.flip2dnd.next.services.FlipDetectorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = FlipDndApp::class, sdk = [28])
class ServiceLifecycleTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun explicitPauseRestoresOwnedSilenceBeforeStopping() = runTest {
        val context = RuntimeEnvironment.getApplication() as FlipDndApp
        val settings = SettingsRepositoryImpl(context)
        settings.setActivationMode(ActivationMode.RINGER)
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val controller = Robolectric.buildService(FlipDetectorService::class.java).create()
        val service = controller.get()
        try {
            runCurrent()
            service.dndRepository.setActivated(true)
            assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
            DetectionControl.pause(context)
            service.onStartCommand(Intent(context, FlipDetectorService::class.java)
                .setAction(DetectionControl.ACTION_PAUSE), 0, 1)
            runCurrent()
            assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
            assertTrue(shadowOf(service).isStoppedBySelf)
            assertFalse(DetectionControl.running.value)
            assertFalse(DetectionControl.isEnabled(context))
        } finally {
            controller.destroy()
            service.dndRepository.onCleared()
        }
    }
}
