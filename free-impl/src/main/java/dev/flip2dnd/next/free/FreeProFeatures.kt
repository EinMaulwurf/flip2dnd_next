package dev.flip2dnd.next.free

import android.widget.Toast
import android.content.Context
import dev.flip2dnd.next.core.ProFeatureManager
import dev.flip2dnd.next.core.UpdateResponse
import dev.flip2dnd.next.core.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FreeProFeatures(context: Context) : ProFeatureManager {
    override fun autoStartEnabled() = true
    override fun proximityDetectionEnabled() = true
    override fun advancedSensitivityEnabled() = true
    override fun delayCustomizationEnabled() = true
    override fun scheduleEnabled() = true
    override fun customSoundEnabled() = true
    override fun batterySaverSyncEnabled() = false
    override fun detectionFiltersEnabled() = true
    override fun telegramSupportEnabled() = false
    override fun flashlightFeedbackEnabled() = false
    override fun isPro() = false

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.None)
    override fun getUpdateState() = _updateState.asStateFlow()
    override fun checkForUpdate(manual: Boolean) {
        // No-op
    }
    
    override fun downloadAndInstall(context: Context, update: UpdateResponse) {
        Toast.makeText(context, "Feature not available in Free version", Toast.LENGTH_SHORT).show()
    }
}
