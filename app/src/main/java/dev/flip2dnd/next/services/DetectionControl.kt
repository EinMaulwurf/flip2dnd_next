package dev.flip2dnd.next.services

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dev.flip2dnd.next.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Desired state survives process death; runtime state is published by the service. */
object DetectionControl {
    const val ACTION_PAUSE = "dev.flip2dnd.next.PAUSE_DETECTION"
    private val mutableEnabled = MutableStateFlow(true)
    val enabled = mutableEnabled.asStateFlow()
    private val mutableRunning = MutableStateFlow(false)
    val running = mutableRunning.asStateFlow()
    private val mutableError = MutableStateFlow<Int?>(null)
    val error = mutableError.asStateFlow()

    private fun preferences(context: Context) =
        context.getSharedPreferences("flip_2_dnd_settings", Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean("detection_enabled", true)
        .also { mutableEnabled.value = it }

    fun resume(context: Context) {
        preferences(context).edit().putBoolean("detection_enabled", true).apply()
        mutableEnabled.value = true
        startIfEnabled(context)
    }

    fun startIfEnabled(context: Context) {
        if (isEnabled(context)) send(context, null)
    }

    fun pause(context: Context) {
        preferences(context).edit().putBoolean("detection_enabled", false).apply()
        mutableEnabled.value = false
        send(context, ACTION_PAUSE)
    }

    private fun send(context: Context, action: String?) {
        try {
            ContextCompat.startForegroundService(context,
                Intent(context, FlipDetectorService::class.java).setAction(action))
        } catch (e: RuntimeException) {
            Log.e("DetectionControl", "Cannot start detection service", e)
            publish(false, R.string.detection_start_failed)
        }
    }

    fun publish(running: Boolean, error: Int? = null) {
        mutableRunning.value = running
        mutableError.value = error
    }
}
