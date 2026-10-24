package dev.flip2dnd.next.core

interface ProFeatureManager {
    fun autoStartEnabled(): Boolean
    fun advancedSensitivityEnabled(): Boolean
    fun delayCustomizationEnabled(): Boolean
    fun scheduleEnabled(): Boolean
    fun customSoundEnabled(): Boolean
    fun batterySaverSyncEnabled(): Boolean
    fun detectionFiltersEnabled(): Boolean
    fun proximityDetectionEnabled(): Boolean = false
    fun telegramSupportEnabled(): Boolean
    fun flashlightFeedbackEnabled(): Boolean
    fun isPro(): Boolean

    fun getUpdateState(): kotlinx.coroutines.flow.StateFlow<UpdateState>
    fun checkForUpdate(manual: Boolean)
    fun downloadAndInstall(context: android.content.Context, update: UpdateResponse)
}
