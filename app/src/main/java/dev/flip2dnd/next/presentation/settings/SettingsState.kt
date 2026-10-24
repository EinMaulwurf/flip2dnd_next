package dev.flip2dnd.next.presentation.settings

data class SettingsState(
    val isScreenOffOnly: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val isSoundEnabled: Boolean = true,
    val isPriorityDndEnabled: Boolean = true
)
