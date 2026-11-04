package dev.flip2dnd.next.services

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import dev.flip2dnd.next.diagnostics.DiagnosticLog

/** Best-effort recovery independent of the detector process; Android may defer execution. */
class DetectionRecoveryService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        if (!DetectionControl.isEnabled(this)) {
            cancel(this)
        } else if (!DetectionControl.running.value) {
            DiagnosticLog.event("detection_recovery_requested")
            // Background-start restrictions are handled and recorded by DetectionControl.
            DetectionControl.startIfEnabled(this)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        internal const val JOB_ID = 2001

        fun schedule(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return
                val job = JobInfo.Builder(JOB_ID, ComponentName(context, DetectionRecoveryService::class.java))
                    .setPeriodic(15 * 60 * 1000L)
                    // Deliberately not persisted: boot startup must respect Auto Start.
                    .build()
                if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
                    DiagnosticLog.event("detection_recovery_schedule_failed")
                }
            } catch (e: RuntimeException) {
                DiagnosticLog.failure("detection_recovery_schedule_failed", e)
            }
        }

        fun cancel(context: Context) {
            (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).cancel(JOB_ID)
        }
    }
}
