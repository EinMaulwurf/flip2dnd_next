package dev.flip2dnd.next.presentation.main

import dev.flip2dnd.next.R
import dev.flip2dnd.next.core.PhoneOrientation

data class MainState(
    val orientation: PhoneOrientation = PhoneOrientation.UNKNOWN,
    val isDndEnabled: Boolean = false,
    val dndMode: Int = R.string.dnd_mode_all,
    val isScreenOffOnly: Boolean = false,
    val isVibrationEnabled: Boolean = true,
    val isSoundEnabled: Boolean = false,
    val isServiceRunning: Boolean = true
)
