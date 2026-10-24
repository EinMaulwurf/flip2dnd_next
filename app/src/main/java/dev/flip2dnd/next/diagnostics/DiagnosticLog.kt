package dev.flip2dnd.next.diagnostics

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.flip2dnd.next.BuildConfig
import dev.flip2dnd.next.core.DiagnosticJournal
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object DiagnosticLog {
    @Volatile private var instance: DiagnosticRecorder? = null
    @Volatile var monitoringMode: String? = null
        private set

    fun modeChanged(mode: String) {
        if (monitoringMode == mode) return
        monitoringMode = mode
        event("monitoring_mode", "mode" to mode)
    }

    @Synchronized fun get(context: Context): DiagnosticRecorder = instance ?: DiagnosticRecorder(context)
        .also { instance = it }

    fun event(name: String, vararg fields: Pair<String, Any?>) { instance?.record(name, *fields) }

    fun failure(name: String, error: Throwable) {
        val recorder = instance ?: return
        if (!recorder.enabled.value) return
        // Exception messages can contain custom file URIs. Capture code locations only.
        recorder.record(name, "error_class" to error.javaClass.name,
            "stack" to error.stackTrace.take(12).joinToString("\n") { it.toString() })
    }
}

/** One IO writer, bounded queue and storage; no polling, raw sensor stream or wake locks. */
class DiagnosticRecorder(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("diagnostics_state", Context.MODE_PRIVATE)
    private val settings = this.context.getSharedPreferences("flip_2_dnd_settings", Context.MODE_PRIVATE)
    private val journal = DiagnosticJournal(File(this.context.noBackupFilesDir, "diagnostics"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<() -> Unit>(256)
    private val dropped = AtomicLong()
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", false))
    val enabled = mutableEnabled.asStateFlow()
    private val mutableStorageError = MutableStateFlow(false)
    val storageError = mutableStorageError.asStateFlow()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    private var observing = false
    private var lastDeviceState: String? = null
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in SAFE_SETTINGS) record("setting_changed", "key" to key, "value" to settings.all[key])
    }
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            enqueue { deviceStateChanged(intent?.action ?: "unknown") }
        }
    }
    private val writer = scope.launch {
        for (command in commands) {
            try { command() }
            catch (e: Exception) {
                mutableStorageError.value = true
                Log.w("Diagnostics", "Cannot write diagnostic data", e)
            }
        }
    }

    init {
        if (enabled.value) {
            runCatching { observeDevice(true) }.onFailure {
                mutableStorageError.value = true
                Log.w("Diagnostics", "Cannot observe device state", it)
            }
            enqueue { append("process_started", mapOf("snapshot" to snapshot())) }
        }
    }

    fun record(name: String, vararg fields: Pair<String, Any?>) {
        if (!enabled.value) return
        val wallTime = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val values = fields.toMap()
        enqueue {
            if (enabled.value) append(name, values, wallTime, elapsed)
        }
    }

    private fun enqueue(command: () -> Unit) {
        if (!commands.trySend(command).isSuccess) dropped.incrementAndGet()
    }

    private suspend fun <T> control(block: () -> T): T {
        val result = CompletableDeferred<T>()
        commands.send {
            try { result.complete(block()) }
            catch (e: Exception) {
                mutableStorageError.value = true
                result.completeExceptionally(e)
            }
        }
        return result.await()
    }

    suspend fun setEnabled(value: Boolean) = control {
        if (value == enabled.value) return@control
        check(preferences.edit().putBoolean("enabled", value).commit()) { "Cannot save diagnostic setting" }
        mutableEnabled.value = value
        observeDevice(value)
        append(if (value) "recording_enabled" else "recording_disabled", mapOf("snapshot" to snapshot()))
    }

    suspend fun clear() = control {
        journal.clear()
        File(context.cacheDir, "diagnostic_exports").listFiles()?.forEach {
            check(!it.exists() || it.delete()) { "Cannot clear diagnostic export" }
        }
        dropped.set(0)
        mutableStorageError.value = false
        if (enabled.value) append("log_cleared", mapOf("snapshot" to snapshot()))
    }

    /** Serialize behind all pending events so export has a well-defined cutoff. */
    suspend fun export(): File = control {
        val directory = File(context.cacheDir, "diagnostic_exports")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create diagnostic export" }
        // Keep the current report and at most two previous exports for sharing apps.
        directory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?.drop(2)?.forEach { it.delete() }
        val file = File.createTempFile("flip2dnd-diagnostics-", ".txt", directory)
        try {
            file.outputStream().buffered().use { output ->
                val header = JSONObject().put("format", "flip2dnd-diagnostics-v1")
                    .put("exported_at", dateFormat.format(Date()))
                    .put("recording_enabled", enabled.value)
                    .put("storage_error", storageError.value)
                    .put("pending_events_dropped", dropped.get())
                    .put("snapshot", snapshot())
                output.write((header.toString() + "\n").toByteArray(Charsets.UTF_8))
                journal.writeTo(output)
            }
            file
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private fun append(name: String, values: Map<String, Any?>,
        wallTime: Long = System.currentTimeMillis(), elapsed: Long = SystemClock.elapsedRealtime()) {
        val lost = dropped.getAndSet(0)
        if (lost > 0) appendRecord("events_dropped", mapOf("count" to lost), wallTime, elapsed)
        appendRecord(name, values, wallTime, elapsed)
        mutableStorageError.value = false
    }

    private fun appendRecord(name: String, values: Map<String, Any?>, wallTime: Long, elapsed: Long) {
        val entry = JSONObject().put("time", dateFormat.format(Date(wallTime)))
            .put("elapsed_ms", elapsed).put("pid", Process.myPid()).put("event", name.take(80))
        values.entries.take(20).forEach { (key, value) -> entry.put(key.take(80), safeValue(value)) }
        check(journal.append(entry.toString())) { "Diagnostic record too large" }
    }

    private fun safeValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Boolean, is Number, is JSONObject, is JSONArray -> value
        is Set<*> -> JSONArray(value.map { it.toString().take(80) }.sorted())
        else -> value.toString().take(4096)
    }

    private fun snapshot(): JSONObject {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val result = JSONObject().put("version_name", BuildConfig.VERSION_NAME)
            .put("version_code", BuildConfig.VERSION_CODE).put("android_sdk", Build.VERSION.SDK_INT)
            .put("android_release", Build.VERSION.RELEASE).put("debug_build", BuildConfig.DEBUG)
            .put("security_patch", Build.VERSION.SECURITY_PATCH).put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL).put("device_state", deviceState())
        val configuration = JSONObject()
        settings.all.filterKeys { it in SAFE_SETTINGS }.toSortedMap()
            .forEach { (key, value) -> configuration.put(key, safeValue(value)) }
        result.put("settings", configuration)
        val session = JSONObject()
        context.getSharedPreferences("flip_dnd_session", Context.MODE_PRIVATE).all
            .filterKeys { it in setOf("mode", "previous", "applied") }
            .forEach { (key, value) -> session.put(key, safeValue(value)) }
        result.put("session", session)
        result.put("sensors", JSONArray(sensors.getSensorList(Sensor.TYPE_ALL)
            .filter { it.type == Sensor.TYPE_ACCELEROMETER || it.type == Sensor.TYPE_PROXIMITY ||
                it.stringType == "android.sensor.tilt_detector" }.map {
                JSONObject().put("type", it.stringType).put("name", it.name).put("vendor", it.vendor)
                    .put("wake_up", it.isWakeUpSensor).put("reporting_mode", it.reportingMode)
            }))
        return result
    }

    private fun deviceState(): JSONObject {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return JSONObject().put("battery_percent", if (scale > 0 && level >= 0) level * 100 / scale else -1)
            .put("plugged", battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0)
            .put("interactive", power.isInteractive).put("device_idle", power.isDeviceIdleMode)
            .put("power_saver", power.isPowerSaveMode)
            .put("battery_optimization_exempt", power.isIgnoringBatteryOptimizations(context.packageName))
            .put("dnd_access", notifications.isNotificationPolicyAccessGranted)
            .put("notifications_allowed", NotificationManagerCompat.from(context).areNotificationsEnabled())
            .put("system_filter", notifications.currentInterruptionFilter).put("ringer", audio.ringerMode)
            .put("detection_enabled", settings.getBoolean("detection_enabled", true))
            .put("monitoring_running", dev.flip2dnd.next.services.DetectionControl.running.value)
            .put("monitoring_mode", DiagnosticLog.monitoringMode ?: "not_running")
            .put("timezone", TimeZone.getDefault().id)
    }

    private fun deviceStateChanged(reason: String) {
        if (!enabled.value) return
        val state = deviceState()
        if (state.toString() == lastDeviceState && reason != Intent.ACTION_TIME_CHANGED) return
        lastDeviceState = state.toString()
        append("device_state_changed", mapOf("reason" to reason, "state" to state))
    }

    private fun observeDevice(value: Boolean) {
        if (value == observing) return
        if (value) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
            }
            ContextCompat.registerReceiver(context, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            settings.registerOnSharedPreferenceChangeListener(settingsListener)
        } else {
            context.unregisterReceiver(stateReceiver)
            settings.unregisterOnSharedPreferenceChangeListener(settingsListener)
        }
        observing = value
    }

    internal suspend fun close() {
        observeDevice(false)
        commands.close()
        writer.join()
        scope.cancel()
    }

    companion object {
        private val SAFE_SETTINGS = setOf("screen_off_only", "turn_screen_off", "activation_mode", "dnd_mode",
            "ringer_mode", "flip_sensitivity", "activation_delay", "proximity_detection", "media_playback_detection",
            "headphone_detection", "flashlight_detection", "schedule_enabled", "schedule_start_time",
            "schedule_end_time", "schedule_days", "auto_start", "detection_enabled", "vibration", "sound",
            "notifications_enabled", "use_custom_vibration", "custom_vibration_strength", "dnd_on_vibration", "dnd_off_vibration")
    }
}
