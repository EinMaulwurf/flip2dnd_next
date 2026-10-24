package dev.flip2dnd.next.services

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioPlaybackConfiguration
import java.util.Calendar
import dev.flip2dnd.next.core.ScheduleWindow
import dev.flip2dnd.next.core.SensorMonitoringSession
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.flip2dnd.next.MainActivity
import dev.flip2dnd.next.R
import dev.flip2dnd.next.core.PhoneOrientation
import dev.flip2dnd.next.core.DndRepository
import dev.flip2dnd.next.core.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

private const val TAG = "FlipDetectorService"
private const val SERVICE_NOTIFICATION_ID = 1
private const val FLIP_NOTIFICATION_ID = 2
private const val DND_NOTIFICATION_ID = 3
private const val SERVICE_CHANNEL_ID = "FlipDetectorChannel"
private const val FLIP_CHANNEL_ID = "FlipStateChannel"
private const val ACTION_REEVALUATE = "dev.flip2dnd.next.REEVALUATE"

@AndroidEntryPoint
class FlipDetectorService : Service() {
    lateinit var sensorService: SensorService
        private set
    private lateinit var dndService: DndService
    private lateinit var powerManager: PowerManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var audioManager: AudioManager
    private var screenReceiverRegistered = false
    private val reevaluations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val scheduleIntent by lazy {
        PendingIntent.getService(this, 10, Intent(this, FlipDetectorService::class.java).setAction(ACTION_REEVALUATE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private val audioDevices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { requestReevaluation() }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { requestReevaluation() }
    }
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
    private var legacyMediaRetry: Job? = null
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var dndRepository: DndRepository
    
    private val initialSettings = mutableListOf<CompletableDeferred<Unit>>()

    private fun observeSetting(block: suspend (CompletableDeferred<Unit>) -> Unit) {
        val ready = CompletableDeferred<Unit>()
        initialSettings += ready
        serviceScope.launch { block(ready) }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    
    private val _isScreenOff = MutableStateFlow(false)
    private val isScreenOff = _isScreenOff.asStateFlow()
    private val _isProximityCovered = MutableStateFlow(false)
    private val isProximityCovered = _isProximityCovered.asStateFlow()
    private var onlyWhenScreenOff = false
    private var turnScreenOff = false
    private var activationDelaySeconds = 0
    private var activatedDuringFaceDown = false
    private var stopping = false
    private var stopJob: Job? = null
    private val pendingActivation = dev.flip2dnd.next.core.PendingActivation(serviceScope) { action ->
        withTransitionWakeLock(this) { sensorService.confirm(action) }
    }

    // DND Activation Schedule settings
    private var dndScheduleEnabled = false
    private var dndScheduleStartTime = "22:00"
    private var dndScheduleEndTime = "07:00"
    private var dndScheduleDays = setOf(1, 2, 3, 4, 5, 6, 7)

    // Helper vars
    private var flashlightDetectionEnabled = false
    private var mediaPlaybackDetectionEnabled = false
    private var headphoneDetectionEnabled = false
    private var proximityDetectionEnabled = false
    private var proximityUnavailable = false



    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (stopping) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen turned OFF")
                    _isScreenOff.value = true
                    sensorService.setScreenOff(true)
                    
                    // Ensure sensors are monitoring when screen is off
                    if (!sensorService.isRegistered) {
                        Log.d(TAG, "Starting sensor monitoring because screen is OFF")
                        sensorService.startMonitoring()
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen turned ON")
                    _isScreenOff.value = false
                    sensorService.setScreenOff(false)
                    
                    // Optimization: if 'Only when screen off' is enabled, stop sensors when screen is on
                    if (onlyWhenScreenOff && !dndService.isAppEnabledDnd.value) {
                        Log.d(TAG, "Stopping sensor monitoring because screen is ON and 'onlyWhenScreenOff' is enabled")
                        sensorService.stopMonitoring()
                    }
                }
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> requestReevaluation()
                Intent.ACTION_SHUTDOWN -> {
                    Log.d(TAG, "System shutdown - Stopping sensor monitoring")
                    sensorService.stopMonitoring()
                }
                Intent.ACTION_REBOOT -> {
                    Log.d(TAG, "System reboot - Stopping sensor monitoring")
                    sensorService.stopMonitoring()
                }
            }
        }
    }

    private fun requestReevaluation() {
        if (stopping) return
        reevaluations.tryEmit(Unit)
        scheduleNextCheck()
    }

    private fun scheduleNextCheck() {
        if (!::alarmManager.isInitialized) return
        alarmManager.cancel(scheduleIntent)
        if (stopping || !dndScheduleEnabled) return
        val next = ScheduleWindow.nextChange(dndScheduleStartTime, dndScheduleEndTime, dndScheduleDays,
            Calendar.getInstance()) ?: return
        // At most actual schedule boundaries wake the CPU; no per-second timer or exact-alarm permission.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, scheduleIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Starting FlipDetectorService")
        val notification = createServiceNotification()
        startForeground(SERVICE_NOTIFICATION_ID, notification)
        if (!DetectionControl.isEnabled(this)) {
            pauseDetection()
            return START_NOT_STICKY
        }
        stopping = false
        if (::sensorService.isInitialized && !sensorService.isRegistered &&
            (!onlyWhenScreenOff || isScreenOff.value || dndService.isAppEnabledDnd.value)) {
            sensorService.startMonitoring()
        }
        if (proximityUnavailable) retryProximity()
        publishState()
        startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification())
        if (intent?.action == ACTION_REEVALUATE) requestReevaluation()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing FlipDetectorService")
        stopping = !DetectionControl.isEnabled(this)
        
        try {
            createNotificationChannels()
            startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification())
            sensorService = SensorService(this, settingsRepository)
            dndService = DndService(this, settingsRepository, dndRepository)
            // settingsRepository is now injected by Hilt
            powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.registerAudioDeviceCallback(audioDevices, Handler(Looper.getMainLooper()))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                playbackCallback = object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                        requestReevaluation()
                    }
                }.also { audioManager.registerAudioPlaybackCallback(it, Handler(Looper.getMainLooper())) }
            }
            
            // Initialize screen state
            _isScreenOff.value = !powerManager.isInteractive
            Log.d(TAG, "Initial screen state: ${if (_isScreenOff.value) "OFF" else "ON"}")
            
            // Register screen state receiver
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SHUTDOWN)
                addAction(Intent.ACTION_REBOOT)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            registerReceiver(screenStateReceiver, filter)
            screenReceiverRegistered = true
            
            // Start sensor monitoring regardless of screen state
            Log.d(TAG, "Starting initial sensor monitoring")
            if (!stopping) sensorService.startMonitoring()
            
            
            // Observe proximity setting
            observeSetting { ready ->
                settingsRepository.getProximityDetectionEnabled().collect { enabled ->
                    proximityDetectionEnabled = enabled
                    ready.complete(Unit)
                    requestReevaluation()
                    Log.d(TAG, "Settings updated - Proximity detection: $enabled")
                    
                    val detectionManager = dev.flip2dnd.next.core.ServiceLocator.getDetectionManager(this@FlipDetectorService)
                    proximityUnavailable = false
                    if (enabled && !stopping) {
                        proximityUnavailable = !detectionManager.registerProximityListener { covered ->
                            _isProximityCovered.value = covered
                        }
                    } else {
                        detectionManager.unregisterProximityListener()
                        _isProximityCovered.value = false
                    }
                    publishState()
                    val notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notifications.notify(SERVICE_NOTIFICATION_ID, createServiceNotification())
                }
            }
            
            // Observe settings changes
            observeSetting { ready ->
                settingsRepository.getScreenOffOnlyEnabled().collect { enabled ->
                    onlyWhenScreenOff = enabled
                    ready.complete(Unit)
                    requestReevaluation()
                    Log.d(TAG, "Settings updated - Only when screen off: $enabled")
                    
                    if (enabled && !isScreenOff.value && !dndService.isAppEnabledDnd.value) {
                        Log.d(TAG, "Stopping sensors because screen is ON and 'onlyWhenScreenOff' was just enabled")
                        sensorService.stopMonitoring()
                    } else if (!stopping && !enabled && !sensorService.isRegistered) {
                        Log.d(TAG, "Starting sensors because 'onlyWhenScreenOff' was just disabled")
                        sensorService.startMonitoring()
                    }
                }
            }

            observeSetting { ready ->
                settingsRepository.getTurnScreenOffEnabled().collect { enabled ->
                    turnScreenOff = enabled
                    ready.complete(Unit)
                    requestReevaluation()
                    Log.d(TAG, "Settings updated - Turn screen off: $enabled")
                }
            }

            observeSetting { ready ->
                settingsRepository.getActivationDelay().collect { delay ->
                    activationDelaySeconds = delay
                    ready.complete(Unit)
                    requestReevaluation()
                    Log.d(TAG, "Settings updated - Activation delay: $delay seconds")
                }
            }
            
            // Observe detection settings
            observeSetting { ready ->
                settingsRepository.getFlashlightDetectionEnabled().collect { enabled ->
                    flashlightDetectionEnabled = enabled
                    ready.complete(Unit)
                    requestReevaluation()
                    Log.d(TAG, "Settings updated - Flashlight detection: $enabled")
                }
            }

            observeSetting { ready ->
                settingsRepository.getMediaPlaybackDetectionEnabled().collect { enabled ->
                    mediaPlaybackDetectionEnabled = enabled
                    ready.complete(Unit)
                    requestReevaluation()
                    Log.d(TAG, "Settings updated - Media playback detection: $enabled")
                }
            }

            observeSetting { ready ->
                settingsRepository.getHeadphoneDetectionEnabled().collect { enabled ->
                    headphoneDetectionEnabled = enabled
                    ready.complete(Unit)
                    requestReevaluation()
                    Log.d(TAG, "Settings updated - Headphone detection: $enabled")
                }
            }


            // Observe DND activation schedule settings
            observeSetting { ready ->
                settingsRepository.getDndScheduleEnabled().collect { enabled ->
                    dndScheduleEnabled = enabled
                    ready.complete(Unit)
                    requestReevaluation()
                    Log.d(TAG, "Settings updated - DND Schedule enabled: $enabled")
                }
            }

            observeSetting { ready ->
                settingsRepository.getDndScheduleStartTime().collect { time ->
                    dndScheduleStartTime = time
                    ready.complete(Unit)
                    requestReevaluation()
                    Log.d(TAG, "Settings updated - DND Schedule start time: $time")
                }
            }

            observeSetting { ready ->
                settingsRepository.getDndScheduleEndTime().collect { time ->
                    dndScheduleEndTime = time
                    ready.complete(Unit)
                    requestReevaluation()
                    Log.d(TAG, "Settings updated - DND Schedule end time: $time")
                }
            }

            observeSetting { ready ->
                settingsRepository.getDndScheduleDays().collect { days ->
                    dndScheduleDays = days
                    ready.complete(Unit)
                    requestReevaluation()
                    Log.d(TAG, "Settings updated - DND Schedule days: $days")
                }
            }

            // Observe orientation changes after all persisted eligibility settings are loaded.
            serviceScope.launch {
                initialSettings.forEach { it.await() }
                merge(
                    sensorService.orientation.map { Unit },
                    isScreenOff.map { Unit },
                    isProximityCovered.map { Unit },
                    dndRepository.isActivated().map { Unit },
                    dndRepository.isOwned().map { Unit },
                    dndService.flashlightOn.map { Unit },
                    reevaluations,
                ).conflate().collect {
                    try {
                        withTransitionWakeLock(this@FlipDetectorService) {
                            handleOrientationChange(sensorService.orientation.value, isScreenOff.value)
                        }
                    } catch (e: TimeoutCancellationException) {
                        Log.e(TAG, "Flip processing exceeded its wake-lock deadline", e)
                    }
                }
            }
            serviceScope.launch {
                sensorService.monitoringMode.collect {
                    publishState()
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(SERVICE_NOTIFICATION_ID, createServiceNotification())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.localizedMessage}", e)
            DetectionControl.publish(false, R.string.detection_start_failed)
            stopSelf()
        }
    }

    private fun canActivate(screenOff: Boolean): Boolean {
        if (!sensorService.isRegistered || proximityUnavailable) return false
        if (onlyWhenScreenOff && !screenOff) return false
        if (dndScheduleEnabled && !isWithinDndSchedule()) return false
        if (flashlightDetectionEnabled && dndService.isFlashlightOn) return false
        val detection = dev.flip2dnd.next.core.ServiceLocator.getDetectionManager(this)
        if (mediaPlaybackDetectionEnabled && detection.isMediaPlaying()) return false
        if (headphoneDetectionEnabled && detection.areHeadphonesConnected()) return false
        if (proximityDetectionEnabled && !isProximityCovered.value) return false
        return true
    }

    private suspend fun handleOrientationChange(orientation: PhoneOrientation, screenOff: Boolean) {
        if (stopping) return
        try {
            dndService.updateDndStatus()
            if (orientation == PhoneOrientation.UNKNOWN) {
                activatedDuringFaceDown = false
                pendingActivation.cancel()
                return
            }
            if (orientation == PhoneOrientation.FACE_DOWN) {
                if (dndService.isDndEnabled.value) {
                    pendingActivation.cancel()
                    activatedDuringFaceDown = true
                    return
                }
                // Respect a manual DND-off change until the next physical flip.
                if (activatedDuringFaceDown) return
                if (!canActivate(screenOff)) {
                    pendingActivation.cancel()
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && mediaPlaybackDetectionEnabled &&
                        dev.flip2dnd.next.core.ServiceLocator.getDetectionManager(this).isMediaPlaying() &&
                        legacyMediaRetry?.isActive != true) {
                        legacyMediaRetry = serviceScope.launch {
                            delay(5000)
                            legacyMediaRetry = null
                            requestReevaluation()
                        }
                    }
                    return
                }
                // Repeated screen/proximity events and filter checks must not restart the delay
                // or cancel feedback for a transition that has already completed.
                pendingActivation.startIfIdle(
                    activationDelaySeconds.coerceAtLeast(0) * 1000L,
                    onStarted = { showFlipDetectedNotification() }
                ) {
                    if (sensorService.orientation.value != PhoneOrientation.FACE_DOWN ||
                        !canActivate(isScreenOff.value)) return@startIfIdle
                    if (dndService.setActivated(true)) {
                        activatedDuringFaceDown = true
                        showDndStateNotification(true)
                        if (!isScreenOff.value && turnScreenOff) {
                            TurnScreenOffService.turnScreenOff(this@FlipDetectorService)
                        }
                        updateBatterySaver(true)
                    }
                }
            } else {
                activatedDuringFaceDown = false
                legacyMediaRetry?.cancel()
                pendingActivation.cancel()
                if (dndService.isAppEnabledDnd.value) {
                    if (dndService.setActivated(false)) {
                        showDndStateNotification(false)
                        updateBatterySaver(false)
                    }
                } else {
                    dndRepository.setActivated(false)
                }
                if (onlyWhenScreenOff && !screenOff) sensorService.stopMonitoring()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error handling orientation change", e)
        }
    }

    private suspend fun updateBatterySaver(enabled: Boolean) {
        try {
            if (settingsRepository.getBatterySaverOnFlipEnabled().first()) {
                dev.flip2dnd.next.core.ServiceLocator.getPowerController(this)
                    .setBatterySaverEnabled(enabled)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error changing battery saver", e)
        }
    }

    private fun retryProximity() {
        proximityUnavailable = !dev.flip2dnd.next.core.ServiceLocator.getDetectionManager(this)
            .registerProximityListener { _isProximityCovered.value = it }
    }

    private fun hardwareError(): Int? = when {
        ::sensorService.isInitialized && sensorService.monitoringMode.value == SensorMonitoringSession.Mode.UNAVAILABLE ->
            R.string.detection_sensor_unavailable
        proximityUnavailable -> R.string.detection_proximity_unavailable
        else -> null
    }

    private fun publishState(error: Int? = null) {
        val failure = if (stopping) error ?: DetectionControl.error.value else error ?: hardwareError()
        DetectionControl.publish(!stopping && ::sensorService.isInitialized && failure == null, failure)
        dev.flip2dnd.next.widget.FlipDndWidgetProvider.updateWidgetUI(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.service.quicksettings.TileService.requestListeningState(this,
                android.content.ComponentName(this,
                    dev.flip2dnd.next.quicksettings.FlipQuickSettingsTileService::class.java))
        }
    }

    private fun pauseDetection() {
        if (stopJob?.isActive == true) return
        stopping = true
        DetectionControl.publish(false)
        publishState()
        stopJob = serviceScope.launch {
            var error: Int? = null
            try {
                withTransitionWakeLock(this@FlipDetectorService) {
                    pendingActivation.cancelAndWait()
                    legacyMediaRetry?.cancel()
                    if (::sensorService.isInitialized) sensorService.stopMonitoring()
                    dndRepository.setActivated(false)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Restoring sound while pausing timed out", e)
                error = R.string.detection_restore_failed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not restore sound while pausing", e)
                error = R.string.detection_restore_failed
            } finally {
                if (DetectionControl.isEnabled(this@FlipDetectorService)) {
                    stopping = false
                    if (::sensorService.isInitialized) sensorService.startMonitoring()
                    requestReevaluation()
                    publishState(error)
                } else {
                    publishState(error)
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Cleaning up FlipDetectorService")
        stopping = true
        publishState(DetectionControl.error.value)
        serviceScope.cancel()
        if (::alarmManager.isInitialized) alarmManager.cancel(scheduleIntent)
        if (screenReceiverRegistered) unregisterReceiver(screenStateReceiver)
        if (::audioManager.isInitialized) {
            audioManager.unregisterAudioDeviceCallback(audioDevices)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                playbackCallback?.let { audioManager.unregisterAudioPlaybackCallback(it) }
            }
        }
        if (::dndService.isInitialized) dndService.cleanup()
        if (::sensorService.isInitialized) sensorService.cleanup()
        dev.flip2dnd.next.core.ServiceLocator.getDetectionManager(this).unregisterProximityListener()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isWithinDndSchedule(): Boolean {
        return dev.flip2dnd.next.core.ServiceLocator.getScheduleManager(this)
            .isWithinSchedule(dndScheduleStartTime, dndScheduleEndTime, dndScheduleDays)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Service channel
        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            getString(R.string.notification_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_service_channel_desc)
        }

        // Flip state channel
        val flipChannel = NotificationChannel(
            FLIP_CHANNEL_ID,
            getString(R.string.notification_flip_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_flip_channel_desc)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }

        notificationManager.createNotificationChannels(listOf(serviceChannel, flipChannel))
    }

    private fun showFlipDetectedNotification() {
        serviceScope.launch {
            val notificationsEnabled = settingsRepository.getNotificationsEnabled().first()
            if (!notificationsEnabled) {
                Log.d(TAG, "Notifications disabled in settings - skipping flip notification")
                return@launch
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationText = if (activationDelaySeconds > 0) {
                getString(R.string.seconds, activationDelaySeconds).let { getString(R.string.notification_dnd_activate_delay, it) }
            } else {
                getString(R.string.notification_dnd_activate_now)
            }
            val notification = NotificationCompat.Builder(this@FlipDetectorService, FLIP_CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_flip_detected_title))
                .setContentText(notificationText)
                .setSmallIcon(R.drawable.ic_stat_flip)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSilent(true) // Make notification silent
                .build()

            notificationManager.notify(FLIP_NOTIFICATION_ID, notification)
            Log.d(TAG, "Flip detected notification shown")
        }
    }

    private fun showDndStateNotification(enabled: Boolean) {
        serviceScope.launch {
            val notificationsEnabled = settingsRepository.getNotificationsEnabled().first()
            if (!notificationsEnabled) {
                Log.d(TAG, "Notifications disabled in settings - skipping DND state notification")
                return@launch
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this@FlipDetectorService, FLIP_CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_dnd_state_changed_title))
                .setContentText(if (enabled) getString(R.string.notification_dnd_activated) else getString(R.string.notification_dnd_deactivated))
                .setSmallIcon(R.drawable.ic_stat_flip)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSilent(true) // Make notification silent
                .build()

            notificationManager.notify(DND_NOTIFICATION_ID, notification)
            Log.d(TAG, "DND state notification shown: DND ${if (enabled) "enabled" else "disabled"}")
        }
    }

    // Moved to PowerController

    private fun createServiceNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(when {
                hardwareError() != null -> hardwareError()!!
                !::sensorService.isInitialized -> R.string.notification_monitoring_text
                sensorService.monitoringMode.value == SensorMonitoringSession.Mode.LEGACY -> R.string.notification_monitoring_higher_power
                sensorService.monitoringMode.value == SensorMonitoringSession.Mode.TILT ||
                    sensorService.monitoringMode.value == SensorMonitoringSession.Mode.WAKE_UP_ACCELEROMETER -> R.string.notification_monitoring_battery_saving
                else -> R.string.notification_monitoring_text
            }))
            .setSmallIcon(R.drawable.ic_stat_flip)
            .setContentIntent(pendingIntent)
            .build()
    }
    // Moved to DetectionManager
}
