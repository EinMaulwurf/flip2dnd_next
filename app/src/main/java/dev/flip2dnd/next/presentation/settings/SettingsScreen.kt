package dev.flip2dnd.next.presentation.settings

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import dev.flip2dnd.next.R
import dev.flip2dnd.next.core.ActivationMode
import dev.flip2dnd.next.core.DndMode
import dev.flip2dnd.next.core.FlashlightPattern
import dev.flip2dnd.next.core.RingerMode
import dev.flip2dnd.next.core.ServiceLocator
import dev.flip2dnd.next.core.Sound
import dev.flip2dnd.next.core.UpdateState
import dev.flip2dnd.next.core.VibrationPattern
import dev.flip2dnd.next.presentation.changelog.ChangelogAccordion
import dev.flip2dnd.next.presentation.changelog.changelogEntries
import dev.flip2dnd.next.services.TurnScreenOffService
import dev.flip2dnd.next.services.TurnScreenOffService.Companion.isAccessibilityPermissionGranted
import dev.flip2dnd.next.utils.getFileNameFromUri
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SettingsContent(
    viewModel: SettingsViewModel = hiltViewModel(),
    onDonateClick: () -> Unit,
    section: Int = 0,
) {
    val context = LocalContext.current
    val hasProximitySensor = remember(context) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        sensors.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY) != null
    }
    fun acceptSound(uri: Uri?, isDndOn: Boolean) {
        if (uri == null) return
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                viewModel.selectCustomSound(uri.toString(), isDndOn)
            } ?: throw java.io.IOException("No file descriptor")
        } catch (e: Exception) {
            Toast.makeText(context, R.string.error_sound_not_accessible, Toast.LENGTH_LONG).show()
        }
    }
    val onSoundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        acceptSound(it, true)
    }
    val offSoundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        acceptSound(it, false)
    }
    var previewDndOn by rememberSaveable { mutableStateOf(true) }
    val previewOnPattern by viewModel.dndOnVibration.collectAsState()
    val previewOffPattern by viewModel.dndOffVibration.collectAsState()
    val packageInfo =
        remember {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.PackageInfoFlags
                            .of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            } catch (e: Exception) {
                null
            }
        }

    val versionName = packageInfo?.versionName ?: stringResource(id = R.string.unknown)
    val versionCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: 0L
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong() ?: 0L
        }

    val isBeta =
        versionName.contains("pre", ignoreCase = true) ||
            versionName.contains("beta", ignoreCase = true)
    // Changelog is now powered by Kotlin data (ChangelogModel.kt). No raw MD rendering here.
    // Kept for compatibility: references to changelog from MD have been removed.
    val scope = rememberCoroutineScope()
    // Import Kotlin-based changelog data source
    // (ChangelogAccordion and changelogEntries are defined in the changelog package)
    // (Imports will be resolved by the compiler; added below in patch)
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val screenOffOnly by viewModel.screenOffOnly.collectAsState()
    val turnScreenOff by viewModel.turnScreenOff.collectAsState()
    val priorityDndEnabled by viewModel.priorityDndEnabled.collectAsState()
    val dndOnSound by viewModel.dndOnSound.collectAsState()
    val dndOffSound by viewModel.dndOffSound.collectAsState()
    val useCustomVolume by viewModel.useCustomVolume.collectAsState()
    val customVolume by viewModel.customVolume.collectAsState()
    val useCustomVibration by viewModel.useCustomVibration.collectAsState()
    val customVibrationStrength by viewModel.customVibrationStrength.collectAsState()
    val headphoneDetectionEnabled by viewModel.headphoneDetectionEnabled.collectAsState()
    val proximityDetectionEnabled by viewModel.proximityDetectionEnabled.collectAsState()

    val dndScheduleEnabled by viewModel.dndScheduleEnabled.collectAsState()
    val dndScheduleStartTime by viewModel.dndScheduleStartTime.collectAsState()
    val dndScheduleEndTime by viewModel.dndScheduleEndTime.collectAsState()
    val dndScheduleDays by viewModel.dndScheduleDays.collectAsState()

    val soundScheduleEnabled by viewModel.soundScheduleEnabled.collectAsState()
    val soundScheduleStartTime by viewModel.soundScheduleStartTime.collectAsState()
    val soundScheduleEndTime by viewModel.soundScheduleEndTime.collectAsState()
    val soundScheduleDays by viewModel.soundScheduleDays.collectAsState()

    val vibrationScheduleEnabled by viewModel.vibrationScheduleEnabled.collectAsState()
    val vibrationScheduleStartTime by viewModel.vibrationScheduleStartTime.collectAsState()
    val vibrationScheduleEndTime by viewModel.vibrationScheduleEndTime.collectAsState()
    val vibrationScheduleDays by viewModel.vibrationScheduleDays.collectAsState()




    val activationMode by viewModel.activationMode.collectAsState()
    val dndModeSetting by viewModel.dndMode.collectAsState()
    val ringerModeSetting by viewModel.ringerMode.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()

    var showUpgradeDialog by remember { mutableStateOf(false) }

    val accessibilityPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (isAccessibilityPermissionGranted(context)) {
                viewModel.setTurnScreenOff(true)
            }
        }

    if (showUpgradeDialog) {
        UpgradeDialog(onDismiss = { showUpgradeDialog = false })
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
    ) {
        if (section == 0) {
            item {
                Column {
                    Text(
                        text = stringResource(id = R.string.flip_behavior),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp),
                    )

                    SettingsSliderItem(
                        title = stringResource(id = R.string.activation_mode),
                        description = stringResource(id = R.string.activation_mode_description),
                        sliderContent = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    val modes =
                                        listOf(
                                            ActivationMode.DND to R.string.mode_dnd,
                                            ActivationMode.RINGER to R.string.mode_ringer,
                                        )

                                    modes.forEach { (mode, labelRes) ->
                                        val isSelected = activationMode == mode
                                        Surface(
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .padding(4.dp)
                                                    .clickable { viewModel.setActivationMode(mode) },
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text(
                                                text = stringResource(id = labelRes),
                                                modifier = Modifier.padding(8.dp),
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )

                    AnimatedContent(
                        targetState = activationMode,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(500)) with fadeOut(animationSpec = tween(500))
                        },
                        label = "mode_transition",
                    ) { mode ->
                        when (mode) {
                            ActivationMode.DND -> {
                                SettingsSliderItem(
                                    title = stringResource(id = R.string.dnd_sub_mode),
                                    sliderContent = {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            val modes =
                                                listOf(
                                                    DndMode.PRIORITY to R.string.dnd_mode_priority,
                                                    DndMode.TOTAL_SILENCE to R.string.dnd_mode_total_silence,
                                                    DndMode.ALARMS_ONLY to R.string.dnd_mode_alarms_only,
                                                )

                                            modes.forEach { (mode, labelRes) ->
                                                val isSelected = dndModeSetting == mode
                                                Surface(
                                                    modifier =
                                                        Modifier
                                                            .weight(1f)
                                                            .padding(2.dp)
                                                            .clickable { viewModel.setDndMode(mode) },
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(8.dp),
                                                ) {
                                                    Text(
                                                        text = stringResource(id = labelRes),
                                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                                        textAlign = TextAlign.Center,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                            }

                            ActivationMode.RINGER -> {
                                SettingsSliderItem(
                                    title = stringResource(id = R.string.ringer_sub_mode),
                                    sliderContent = {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            val modes =
                                                listOf(
                                                    RingerMode.SILENT to R.string.ringer_silent,
                                                    RingerMode.VIBRATE to R.string.ringer_vibrate,
                                                    RingerMode.NORMAL to R.string.ringer_normal,
                                                )

                                            modes.forEach { (mode, labelRes) ->
                                                val isSelected = ringerModeSetting == mode
                                                Surface(
                                                    modifier =
                                                        Modifier
                                                            .weight(1f)
                                                            .padding(2.dp)
                                                            .clickable { viewModel.setRingerMode(mode) },
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(8.dp),
                                                ) {
                                                    Text(
                                                        text = stringResource(id = labelRes),
                                                        modifier = Modifier.padding(8.dp),
                                                        textAlign = TextAlign.Center,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    SettingsSwitchItem(
                        title = stringResource(id = R.string.screen_off_only),
                        description = stringResource(id = R.string.screen_off_only_description),
                        checked = screenOffOnly,
                        onCheckedChange = { viewModel.setScreenOffOnly(it) },
                    )

                    if (TurnScreenOffService.isTurnScreenOffSupported()) {
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.turn_screen_off),
                            description =
                                stringResource(
                                    id =
                                        if (screenOffOnly) {
                                            R.string.turn_screen_off_description_disabled
                                        } else {
                                            R.string.turn_screen_off_description
                                        },
                                ),
                            checked = turnScreenOff,
                            enabled = !screenOffOnly,
                            onCheckedChange = {
                                if (isAccessibilityPermissionGranted(context)) {
                                    viewModel.setTurnScreenOff(it)
                                } else {
                                    val intent = TurnScreenOffService.getRequestAccessibilityPermissionIntent()
                                    accessibilityPermissionLauncher.launch(intent)
                                }
                            },
                        )
                    }

                    SettingsSliderItem(
                        title = stringResource(id = R.string.activation_delay),
                        description = stringResource(id = R.string.activation_delay_description),
                        isPro = false,
                        sliderContent = {
                            val activationDelay by viewModel.activationDelay.collectAsState()
                            var sliderPosition by remember { mutableStateOf(activationDelay.toFloat()) }
                            LaunchedEffect(activationDelay) {
                                sliderPosition = activationDelay.toFloat()
                            }
                            val isPro = ServiceLocator.getFeatureManager(context).isPro()
                            val delayCustomizationEnabled =
                                ServiceLocator.getFeatureManager(context).delayCustomizationEnabled()
                            Column {
                                Slider(
                                    value = sliderPosition,
                                    onValueChange = { newValue ->
                                        val snappedValue =
                                            kotlin.math
                                                .round(newValue)
                                                .toInt()
                                                .coerceIn(0, 10)
                                        if (delayCustomizationEnabled) {
                                            sliderPosition = snappedValue.toFloat()
                                        } else {
                                            showUpgradeDialog = true
                                        }
                                    },
                                    onValueChangeFinished = {
                                        if (delayCustomizationEnabled) {
                                            viewModel.setActivationDelay(sliderPosition.toInt())
                                        }
                                    },
                                    valueRange = 0f..10f,
                                    steps = 9,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = stringResource(id = R.string.seconds, sliderPosition.toInt()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = if (delayCustomizationEnabled) 1f else 0.5f,
                                        ),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        },
                    )

                    SettingsSliderItem(
                        title = stringResource(id = R.string.flip_sensitivity),
                        description = stringResource(id = R.string.flip_sensitivity_description),
                        sliderContent = {
                            val flipSensitivity by viewModel.flipSensitivity.collectAsState()
                            var sliderPosition by remember { mutableStateOf(flipSensitivity) }

                            LaunchedEffect(flipSensitivity) {
                                sliderPosition = flipSensitivity
                            }

                            Slider(
                                value = sliderPosition,
                                onValueChange = { newSensitivity ->
                                    val steps = listOf(0f, 0.17f, 0.33f, 0.5f, 0.67f, 0.83f, 1f)
                                    val nearestStep =
                                        steps.minByOrNull { kotlin.math.abs(it - newSensitivity) } ?: newSensitivity
                                    sliderPosition = nearestStep
                                },
                                onValueChangeFinished = {
                                    viewModel.setFlipSensitivity(sliderPosition)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                steps = 6,
                            )
                        },
                    )

                    ScheduleSection(
                        title = stringResource(id = R.string.dnd_activation_schedule),
                        enabled = dndScheduleEnabled,
                        onEnabledChange = {
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .scheduleEnabled()
                            ) {
                                viewModel.setDndScheduleEnabled(it)
                            } else {
                                showUpgradeDialog = true
                            }
                        },
                        description = stringResource(id = R.string.dnd_schedule_description),
                        startTime = dndScheduleStartTime,
                        onStartTimeChange = { viewModel.setDndScheduleStartTime(it) },
                        endTime = dndScheduleEndTime,
                        onEndTimeChange = { viewModel.setDndScheduleEndTime(it) },
                        selectedDays = dndScheduleDays,
                        onDaysChange = { viewModel.setDndScheduleDays(it) },
                        alpha =
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .scheduleEnabled()
                            ) {
                                1f
                            } else {
                                0.5f
                            },
                        isPro = false,
                    )
                }
            }
        }

        if (section == 1) {
            item {
                Column {
                    Text(
                        text = stringResource(id = R.string.feedback),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp),
                    )

                    SettingsSwitchItem(
                        title = stringResource(id = R.string.notifications_enabled),
                        description = stringResource(id = R.string.notifications_enabled_description),
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sound Subsection
                    SettingsSubsectionHeader(
                        title = stringResource(id = R.string.sound_feedback),
                        expanded = soundEnabled,
                        onExpandedChange = { viewModel.setSoundEnabled(it) },
                    )

                    AnimatedVisibility(visible = soundEnabled) {
                        Column {
                            var dndOnExpanded by remember { mutableStateOf(false) }
                            var dndOffExpanded by remember { mutableStateOf(false) }
                            val soundSheetState = rememberModalBottomSheetState()

                            val dndOnCustomSoundUri by viewModel.dndOnCustomSoundUri.collectAsState()
                            val dndOnCustomSoundName =
                                remember(dndOnCustomSoundUri) {
                                    dndOnCustomSoundUri?.let { uriString ->
                                        getFileNameFromUri(context, Uri.parse(uriString))
                                    } ?: context.getString(R.string.none_selected)
                                }

                            SettingsClickableItem(
                                title = stringResource(id = R.string.dnd_on_sound),
                                description =
                                    if (dndOnSound ==
                                        Sound.CUSTOM
                                    ) {
                                        stringResource(R.string.custom_sound_format, dndOnCustomSoundName)
                                    } else {
                                        stringResource(dndOnSound.stringResId)
                                    },
                                trailingIcon = {
                                    Icon(Icons.Default.ArrowDropDown, stringResource(R.string.select_sound))
                                },
                                onClick = { dndOnExpanded = true },
                            )

                            if (dndOnExpanded) {
                                ModalBottomSheet(
                                    onDismissRequest = { dndOnExpanded = false },
                                    sheetState = soundSheetState,
                                ) {
                                    Column {
                                        viewModel.availableSounds.forEach { sound ->
                                            SettingsClickableItem(
                                                title = stringResource(sound.stringResId),
                                                trailingIcon = {
                                                    if (sound == dndOnSound) {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_radio_button_checked),
                                                            contentDescription = stringResource(R.string.selected),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                    } else {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_radio_button_unchecked),
                                                            contentDescription = stringResource(R.string.not_selected),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    if (sound == Sound.CUSTOM) {
                                                        if (ServiceLocator
                                                                .getFeatureManager(context)
                                                                .customSoundEnabled()
                                                        ) {
                                                            onSoundPicker.launch(arrayOf("audio/*"))
                                                        } else {
                                                            showUpgradeDialog = true
                                                        }
                                                    } else {
                                                        viewModel.setDndOnSound(sound)
                                                        viewModel.playSelectedSound(sound)
                                                    }
                                                    dndOnExpanded = false
                                                },
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }

                            val dndOffCustomSoundUri by viewModel.dndOffCustomSoundUri.collectAsState()
                            val dndOffCustomSoundName =
                                remember(dndOffCustomSoundUri) {
                                    dndOffCustomSoundUri?.let { uriString ->
                                        getFileNameFromUri(context, Uri.parse(uriString))
                                    } ?: context.getString(R.string.none_selected)
                                }

                            SettingsClickableItem(
                                title = stringResource(id = R.string.dnd_off_sound),
                                description =
                                    if (dndOffSound ==
                                        Sound.CUSTOM
                                    ) {
                                        stringResource(R.string.custom_sound_format, dndOffCustomSoundName)
                                    } else {
                                        stringResource(dndOffSound.stringResId)
                                    },
                                trailingIcon = {
                                    Icon(Icons.Default.ArrowDropDown, stringResource(R.string.select_sound))
                                },
                                onClick = { dndOffExpanded = true },
                            )

                            if (dndOffExpanded) {
                                ModalBottomSheet(
                                    onDismissRequest = { dndOffExpanded = false },
                                    sheetState = soundSheetState,
                                ) {
                                    Column {
                                        viewModel.availableSounds.forEach { sound ->
                                            SettingsClickableItem(
                                                title = stringResource(sound.stringResId),
                                                trailingIcon = {
                                                    if (sound == dndOffSound) {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_radio_button_checked),
                                                            contentDescription = stringResource(R.string.selected),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                    } else {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_radio_button_unchecked),
                                                            contentDescription = stringResource(R.string.not_selected),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    if (sound == Sound.CUSTOM) {
                                                        if (ServiceLocator
                                                                .getFeatureManager(context)
                                                                .customSoundEnabled()
                                                        ) {
                                                            offSoundPicker.launch(arrayOf("audio/*"))
                                                        } else {
                                                            showUpgradeDialog = true
                                                        }
                                                    } else {
                                                        viewModel.setDndOffSound(sound)
                                                        viewModel.playSelectedSound(sound, isForDndOn = false)
                                                    }
                                                    dndOffExpanded = false
                                                },
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }

                            SettingsSwitchItem(
                                title = stringResource(id = R.string.use_custom_volume),
                                description = stringResource(id = R.string.use_custom_volume_description),
                                checked = useCustomVolume,
                                onCheckedChange = { viewModel.setUseCustomVolume(it) },
                            )

                            AnimatedVisibility(visible = useCustomVolume) {
                                SettingsSliderItem(
                                    title = stringResource(id = R.string.custom_volume),
                                    sliderContent = {
                                        var sliderPosition by remember { mutableStateOf(customVolume) }
                                        LaunchedEffect(customVolume) {
                                            sliderPosition = customVolume
                                        }
                                        Slider(
                                            value = sliderPosition,
                                            onValueChange = { newVolume ->
                                                val steps = listOf(0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f)
                                                val nearestStep =
                                                    steps.minByOrNull { kotlin.math.abs(it - newVolume) } ?: newVolume
                                                sliderPosition = nearestStep
                                            },
                                            onValueChangeFinished = {
                                                viewModel.setCustomVolume(sliderPosition)
                                                viewModel.playSelectedSound(viewModel.dndOnSound.value)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            steps = 9,
                                        )
                                    },
                                )
                            }

                            ScheduleSection(
                                title = null,
                                enabled = soundScheduleEnabled,
                                onEnabledChange = {
                                    if (ServiceLocator
                                            .getFeatureManager(context)
                                            .scheduleEnabled()
                                    ) {
                                        viewModel.setSoundScheduleEnabled(it)
                                    } else {
                                        showUpgradeDialog = true
                                    }
                                },
                                description = stringResource(id = R.string.sound_schedule_description),
                                startTime = soundScheduleStartTime,
                                onStartTimeChange = { viewModel.setSoundScheduleStartTime(it) },
                                endTime = soundScheduleEndTime,
                                onEndTimeChange = { viewModel.setSoundScheduleEndTime(it) },
                                selectedDays = soundScheduleDays,
                                onDaysChange = { viewModel.setSoundScheduleDays(it) },
                                alpha =
                                    if (ServiceLocator
                                            .getFeatureManager(context)
                                            .scheduleEnabled()
                                    ) {
                                        1f
                                    } else {
                                        0.5f
                                    },
                            )
                        }
                    }

                    // Vibration Subsection
                    SettingsSubsectionHeader(
                        title = stringResource(id = R.string.vibration_feedback),
                        expanded = vibrationEnabled,
                        onExpandedChange = { viewModel.setVibrationEnabled(it) },
                    )

                    AnimatedVisibility(visible = vibrationEnabled) {
                        Column {
                            var dndOnVibrationExpanded by remember { mutableStateOf(false) }
                            var dndOffVibrationExpanded by remember { mutableStateOf(false) }
                            val vibrationSheetState = rememberModalBottomSheetState()

                            SettingsClickableItem(
                                title = stringResource(id = R.string.dnd_on_vibration_pattern),
                                description =
                                    stringResource(
                                        viewModel.dndOnVibration
                                            .collectAsState()
                                            .value.stringResId,
                                    ),
                                trailingIcon = {
                                    Icon(Icons.Default.ArrowDropDown, stringResource(R.string.select_vibration_pattern))
                                },
                                onClick = { dndOnVibrationExpanded = true },
                            )

                            if (dndOnVibrationExpanded) {
                                ModalBottomSheet(
                                    onDismissRequest = { dndOnVibrationExpanded = false },
                                    sheetState = vibrationSheetState,
                                ) {
                                    Column {
                                        viewModel.availableVibrationPatterns.forEach { pattern ->
                                            SettingsClickableItem(
                                                title = stringResource(pattern.stringResId),
                                                trailingIcon = {
                                                    if (pattern == viewModel.dndOnVibration.collectAsState().value) {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_radio_button_checked),
                                                            contentDescription = stringResource(R.string.selected),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                    } else {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_radio_button_unchecked),
                                                            contentDescription = stringResource(R.string.not_selected),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    previewDndOn = true
                                                    viewModel.setDndOnVibration(pattern)
                                                    viewModel.playSelectedVibration(pattern)
                                                    dndOnVibrationExpanded = false
                                                },
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }

                            SettingsClickableItem(
                                title = stringResource(id = R.string.dnd_off_vibration_pattern),
                                description =
                                    stringResource(
                                        viewModel.dndOffVibration
                                            .collectAsState()
                                            .value.stringResId,
                                    ),
                                trailingIcon = {
                                    Icon(Icons.Default.ArrowDropDown, stringResource(R.string.select_vibration_pattern))
                                },
                                onClick = { dndOffVibrationExpanded = true },
                            )

                            if (dndOffVibrationExpanded) {
                                ModalBottomSheet(
                                    onDismissRequest = { dndOffVibrationExpanded = false },
                                    sheetState = vibrationSheetState,
                                ) {
                                    Column {
                                        viewModel.availableVibrationPatterns.forEach { pattern ->
                                            SettingsClickableItem(
                                                title = stringResource(pattern.stringResId),
                                                trailingIcon = {
                                                    if (pattern == viewModel.dndOffVibration.collectAsState().value) {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_radio_button_checked),
                                                            contentDescription = stringResource(R.string.selected),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                    } else {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_radio_button_unchecked),
                                                            contentDescription = stringResource(R.string.not_selected),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    previewDndOn = false
                                                    viewModel.setDndOffVibration(pattern)
                                                    viewModel.playSelectedVibration(pattern)
                                                    dndOffVibrationExpanded = false
                                                },
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }

                            SettingsSwitchItem(
                                title = stringResource(id = R.string.use_custom_vibration_strength),
                                description = stringResource(id = R.string.use_custom_vibration_description),
                                checked = useCustomVibration,
                                onCheckedChange = { viewModel.setUseCustomVibration(it) },
                            )

                            AnimatedVisibility(visible = useCustomVibration) {
                                SettingsSliderItem(
                                    title = stringResource(id = R.string.custom_vibration_strength),
                                    sliderContent = {
                                        var sliderPosition by remember { mutableStateOf(customVibrationStrength) }
                                        LaunchedEffect(customVibrationStrength) {
                                            sliderPosition = customVibrationStrength
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            androidx.compose.material3.FilterChip(modifier = Modifier.weight(1f), selected = previewDndOn,
                                                onClick = { previewDndOn = true; viewModel.playSelectedVibration(previewOnPattern, sliderPosition) },
                                                label = { Text(stringResource(R.string.dnd_on)) })
                                            androidx.compose.material3.FilterChip(modifier = Modifier.weight(1f), selected = !previewDndOn,
                                                onClick = { previewDndOn = false; viewModel.playSelectedVibration(previewOffPattern, sliderPosition) },
                                                label = { Text(stringResource(R.string.dnd_off)) })
                                        }
                                        Text(stringResource(R.string.vibration_strength_percent, (sliderPosition * 100).toInt()),
                                            style = MaterialTheme.typography.bodySmall)
                                        Slider(
                                            value = sliderPosition,
                                            onValueChange = { newStrength ->
                                                sliderPosition = newStrength
                                            },
                                            onValueChangeFinished = {
                                                viewModel.setCustomVibrationStrength(sliderPosition)
                                                viewModel.playSelectedVibration(if (previewDndOn) previewOnPattern else previewOffPattern, sliderPosition)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            valueRange = 0f..1f,
                                        )
                                    },
                                )
                            }

                            ScheduleSection(
                                title = null,
                                enabled = vibrationScheduleEnabled,
                                onEnabledChange = {
                                    if (ServiceLocator
                                            .getFeatureManager(context)
                                            .scheduleEnabled()
                                    ) {
                                        viewModel.setVibrationScheduleEnabled(it)
                                    } else {
                                        showUpgradeDialog = true
                                    }
                                },
                                description = stringResource(id = R.string.vibration_schedule_description),
                                startTime = vibrationScheduleStartTime,
                                onStartTimeChange = { viewModel.setVibrationScheduleStartTime(it) },
                                endTime = vibrationScheduleEndTime,
                                onEndTimeChange = { viewModel.setVibrationScheduleEndTime(it) },
                                selectedDays = vibrationScheduleDays,
                                onDaysChange = { viewModel.setVibrationScheduleDays(it) },
                                alpha =
                                    if (ServiceLocator
                                            .getFeatureManager(context)
                                            .scheduleEnabled()
                                    ) {
                                        1f
                                    } else {
                                        0.5f
                                    },
                            )
                        }
                    }


                }
            }
        }

        if (section == 2) {
            item {
                Column {
                    Text(
                        text = stringResource(id = R.string.advanced_filters),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp),
                    )

                    val flashlightDetectionEnabled by viewModel.flashlightDetectionEnabled.collectAsState()
                    SettingsSwitchItem(
                        title = stringResource(id = R.string.flashlight_detection),
                        description = stringResource(id = R.string.flashlight_detection_description),
                        checked = flashlightDetectionEnabled,
                        onCheckedChange = {
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .detectionFiltersEnabled()
                            ) {
                                viewModel.setFlashlightDetectionEnabled(it)
                            } else {
                                showUpgradeDialog = true
                            }
                        },
                        alpha =
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .detectionFiltersEnabled()
                            ) {
                                1f
                            } else {
                                0.5f
                            },
                        isPro = false,
                    )

                    val mediaPlaybackDetectionEnabled by viewModel.mediaPlaybackDetectionEnabled.collectAsState()
                    SettingsSwitchItem(
                        title = stringResource(id = R.string.media_playback_detection),
                        description = stringResource(id = R.string.media_playback_detection_description),
                        checked = mediaPlaybackDetectionEnabled,
                        onCheckedChange = {
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .detectionFiltersEnabled()
                            ) {
                                viewModel.setMediaPlaybackDetectionEnabled(it)
                            } else {
                                showUpgradeDialog = true
                            }
                        },
                        alpha =
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .detectionFiltersEnabled()
                            ) {
                                1f
                            } else {
                                0.5f
                            },
                        isPro = false,
                    )

                    val headphoneDetectionEnabled by viewModel.headphoneDetectionEnabled.collectAsState()
                    SettingsSwitchItem(
                        title = stringResource(id = R.string.headphone_detection),
                        description = stringResource(id = R.string.headphone_detection_description),
                        checked = headphoneDetectionEnabled,
                        onCheckedChange = {
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .detectionFiltersEnabled()
                            ) {
                                viewModel.setHeadphoneDetectionEnabled(it)
                            } else {
                                showUpgradeDialog = true
                            }
                        },
                        alpha =
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .detectionFiltersEnabled()
                            ) {
                                1f
                            } else {
                                0.5f
                            },
                        isPro = false,
                    )

                    SettingsSwitchItem(
                        title = stringResource(id = R.string.proximity_detection),
                        description = stringResource(if (hasProximitySensor) R.string.proximity_detection_description else R.string.proximity_unavailable),
                        enabled = hasProximitySensor || proximityDetectionEnabled,
                        checked = proximityDetectionEnabled,
                        onCheckedChange = {
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .proximityDetectionEnabled()
                            ) {
                                viewModel.setProximityDetectionEnabled(it)
                            } else {
                                showUpgradeDialog = true
                            }
                        },
                        alpha =
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .proximityDetectionEnabled()
                            ) {
                                1f
                            } else {
                                0.5f
                            },
                        isPro = false,
                    )
                }
            }
        }

        if (section == 3) {
            item {
                Column {
                    Text(
                        text = stringResource(id = R.string.app_settings),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp),
                    )

                    val autoStartEnabled by viewModel.autoStartEnabled.collectAsState()
                    SettingsSwitchItem(
                        title = stringResource(id = R.string.auto_start),
                        description = stringResource(id = R.string.auto_start_description),
                        checked = autoStartEnabled,
                        onCheckedChange = {
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .autoStartEnabled()
                            ) {
                                viewModel.setAutoStartEnabled(it)
                            } else {
                                showUpgradeDialog = true
                            }
                        },
                        alpha =
                            if (ServiceLocator
                                    .getFeatureManager(context)
                                    .autoStartEnabled()
                            ) {
                                1f
                            } else {
                                0.5f
                            },
                        isPro = false,
                    )
                    if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                        Build.MANUFACTURER.equals("Redmi", ignoreCase = true)) {
                        Text(stringResource(R.string.xiaomi_autostart_hint),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }


                    // High sensitivity feature removed from display. Always-on by default.

                    SettingsClickableItem(
                        title = stringResource(id = R.string.language),
                        description = stringResource(id = R.string.language_description),
                        onClick = {
                            try {
                                val intent =
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS)
                                    } else {
                                        Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS)
                                    }
                                intent.data = Uri.fromParts("package", context.packageName, null)
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.error_opening_language_settings,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        },
                    )

                    SettingsClickableItem(
                        title = stringResource(id = R.string.dnd_settings),
                        description = stringResource(id = R.string.dnd_settings_description),
                        onClick = {
                            try {
                                val intent = Intent("android.settings.ZEN_MODE_AUTOMATION_SETTINGS")
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.error_opening_dnd_settings,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        },
                    )
                }
            }
        }

        if (section == 4) {
            item {
                Column {
                    Text(
                        text = stringResource(id = R.string.extras),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp),
                    )

                    SettingsClickableItem(
                        title = "GitHub",
                        description = "EinMaulwurf/flip2dnd_next",
                        leadingIcon = { Icon(painterResource(R.drawable.ic_github), contentDescription = null) },
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/EinMaulwurf/flip2dnd_next")))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, R.string.error_no_app_found, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )

                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.version), style = MaterialTheme.typography.labelMedium)
                        Text("$versionName ($versionCode)", style = MaterialTheme.typography.bodyMedium)
                    }


                }
            }
        }


    }
}

@Composable
fun ScheduleSection(
    title: String? = null,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    description: String,
    startTime: String,
    onStartTimeChange: (String) -> Unit,
    endTime: String,
    onEndTimeChange: (String) -> Unit,
    selectedDays: Set<Int>,
    onDaysChange: (Set<Int>) -> Unit,
    alpha: Float = 1f,
    isPro: Boolean = false,
) {
    val context = LocalContext.current
    val featureManager = ServiceLocator.getFeatureManager(context)

    Column(modifier = Modifier.alpha(alpha)) {
        if (title != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isPro && !featureManager.isPro()) {
                    ProBadge(modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        SettingsSwitchItem(
            title = stringResource(id = R.string.schedule_enabled),
            description = description,
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )

        AnimatedVisibility(visible = enabled) {
            Column {
                SettingsClickableItem(
                    title = stringResource(id = R.string.start_time),
                    description = startTime,
                    onClick = {
                        val parts = startTime.split(":")
                        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
                        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                onStartTimeChange(String.format("%02d:%02d", h, m))
                            },
                            hour,
                            minute,
                            true,
                        ).show()
                    },
                )

                SettingsClickableItem(
                    title = stringResource(id = R.string.end_time),
                    description = endTime,
                    onClick = {
                        val parts = endTime.split(":")
                        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
                        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                onEndTimeChange(String.format("%02d:%02d", h, m))
                            },
                            hour,
                            minute,
                            true,
                        ).show()
                    },
                )

                Text(
                    text = stringResource(id = R.string.days),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                DayPicker(
                    selectedDays = selectedDays,
                    onDaysChange = onDaysChange,
                )
            }
        }
    }
}

@Composable
private fun DayPicker(
    selectedDays: Set<Int>,
    onDaysChange: (Set<Int>) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val daysOfWeek =
            listOf(
                R.string.day_sunday_short,
                R.string.day_monday_short,
                R.string.day_tuesday_short,
                R.string.day_wednesday_short,
                R.string.day_thursday_short,
                R.string.day_friday_short,
                R.string.day_saturday_short,
            )
        daysOfWeek.forEachIndexed { index, dayResId ->
            val dayValue = index + 1
            val isSelected = selectedDays.contains(dayValue)
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ).clickable {
                            val newDays =
                                if (isSelected) {
                                    selectedDays - dayValue
                                } else {
                                    selectedDays + dayValue
                                }
                            onDaysChange(newDays)
                        },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(dayResId),
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    alpha: Float = 1f,
    isPro: Boolean = false,
) {
    val context = LocalContext.current
    val featureManager = ServiceLocator.getFeatureManager(context)
    Card(
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (checked) {
                        MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.3f,
                        )
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 0.dp,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .alpha(alpha),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isPro && !featureManager.isPro()) {
                        ProBadge(modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
fun SettingsSliderItem(
    title: String,
    description: String? = null,
    isPro: Boolean = false,
    sliderContent: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val featureManager = ServiceLocator.getFeatureManager(context)
    var showUpgradeDialog by remember { mutableStateOf(false) }

    if (showUpgradeDialog) {
        UpgradeDialog(onDismiss = { showUpgradeDialog = false })
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 0.dp,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(MaterialTheme.shapes.large)
                .then(
                    if (isPro && !featureManager.isPro()) {
                        Modifier.clickable { showUpgradeDialog = true }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isPro && !featureManager.isPro()) {
                    ProBadge(modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            sliderContent()
        }
    }
}

@Composable
fun SettingsClickableItem(
    title: String,
    description: String? = null,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    alpha: Float = 1f,
    isPro: Boolean = false,
) {
    val context = LocalContext.current
    val featureManager = ServiceLocator.getFeatureManager(context)
    Card(
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 0.dp,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(alpha),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (leadingIcon != null) {
                Box(modifier = Modifier.size(24.dp)) {
                    leadingIcon()
                }
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isPro && !featureManager.isPro()) {
                        ProBadge(modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.pro),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun SettingsSubsectionHeader(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onExpandedChange(!expanded) },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Switch(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }
}
