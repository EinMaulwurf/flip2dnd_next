package dev.flip2dnd.next

import android.app.Application
import android.app.job.JobScheduler
import android.content.Context
import dev.flip2dnd.next.services.DetectionControl
import dev.flip2dnd.next.services.DetectionRecoveryService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DetectionRecoveryTest {
    private val context get() = RuntimeEnvironment.getApplication() as Application
    private val scheduler get() = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    @Before fun setup() {
        DetectionControl.publish(false)
        context.getSharedPreferences("flip_2_dnd_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun recoverySurvivesLostRuntimeStateAndStartsDetector() {
        DetectionRecoveryService.schedule(context)
        val service = Robolectric.buildService(DetectionRecoveryService::class.java).create()
        try {
            assertFalse(service.get().onStartJob(null))
            assertEquals("dev.flip2dnd.next.services.FlipDetectorService",
                shadowOf(context).nextStartedService.component?.className)
        } finally { service.destroy() }
    }

    @Test fun healthyDetectorIsLeftRunning() {
        DetectionControl.publish(true)
        val service = Robolectric.buildService(DetectionRecoveryService::class.java).create()
        try {
            service.get().onStartJob(null)
            assertNull(shadowOf(context).nextStartedService)
        } finally {
            service.destroy()
            DetectionControl.publish(false)
        }
    }

    @Test fun pauseCancelsRecoveryAndStaleJobCannotResumeDetection() {
        DetectionRecoveryService.schedule(context)
        DetectionControl.pause(context)
        shadowOf(context).nextStartedService // Consume the explicit pause command.
        assertTrue(scheduler.allPendingJobs.isEmpty())
        val service = Robolectric.buildService(DetectionRecoveryService::class.java).create()
        try {
            service.get().onStartJob(null)
            assertNull(shadowOf(context).nextStartedService)
        } finally { service.destroy() }
    }

    @Test fun schedulingIsIdempotentAndDoesNotBypassBootPreference() {
        DetectionRecoveryService.schedule(context)
        DetectionRecoveryService.schedule(context)
        val job = scheduler.allPendingJobs.single()
        assertTrue(job.isPeriodic)
        assertEquals(15 * 60 * 1000L, job.intervalMillis)
        assertFalse(job.isPersisted)
    }
}
