package dev.flip2dnd.next.services

import android.content.Context
import android.content.Intent
import dev.flip2dnd.next.core.SettingsRepository
import dev.flip2dnd.next.core.Sound
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SoundService(
    private val context: android.content.Context,
    private val settingsRepository: SettingsRepository
) {
    
    companion object {
        private const val TAG = "SoundService"
    }

    fun playDndSound(isEnabled: Boolean) {
        runBlocking {
            val isSoundEnabled = settingsRepository.getSoundEnabled().first()
            if (!isSoundEnabled) return@runBlocking

            try {
                // Get the appropriate sound and settings based on DND state
                val sound = if (isEnabled) {
                    settingsRepository.getDndOnSound().first()
                } else {
                    settingsRepository.getDndOffSound().first()
                }

                if (sound == Sound.NONE) return@runBlocking

                val uri = if (isEnabled) {
                    settingsRepository.getDndOnCustomSoundUri().first()
                } else {
                    settingsRepository.getDndOffCustomSoundUri().first()
                }
                
                val volume = settingsRepository.getCustomVolume().first()
                val useCustomVolume = settingsRepository.getUseCustomVolume().first()
                
                val soundController = dev.flip2dnd.next.core.ServiceLocator.getSoundController(context)
                soundController.playSound(sound, uri, volume, useCustomVolume)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error playing sound: ${e.message}", e)
            }
        }
    }

    fun release() {
        dev.flip2dnd.next.core.ServiceLocator.getSoundController(context).release()
    }
}
