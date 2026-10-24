package dev.flip2dnd.next.free

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import dev.flip2dnd.next.core.Sound
import dev.flip2dnd.next.core.SoundController

class FreeSoundController(context: Context) : SoundController {
    private val context = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null

    override fun playSound(sound: Sound, uri: String?, volume: Float, useCustomVolume: Boolean) {
        stopSound()
        if (sound == Sound.NONE || (sound == Sound.CUSTOM && uri.isNullOrBlank())) return
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM).build())
            when (sound) {
                Sound.CUSTOM -> player.setDataSource(context, Uri.parse(uri))
                Sound.SYSTEM_DEFAULT -> player.setDataSource(context,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                else -> context.resources.openRawResourceFd(sound.soundResId).use {
                    player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
            val level = if (useCustomVolume) volume.coerceIn(0f, 1f) else 1f
            player.setVolume(level, level)
            player.setOnPreparedListener { if (mediaPlayer === it) it.start() }
            player.setOnCompletionListener { finish(it) }
            player.setOnErrorListener { failed, _, _ -> finish(failed); true }
            player.prepareAsync()
        } catch (e: Exception) {
            finish(player)
            Log.w("SoundController", "Cannot play selected sound (the file may have moved)", e)
        }
    }

    private fun finish(player: MediaPlayer) {
        if (mediaPlayer === player) mediaPlayer = null
        player.release()
    }

    override fun previewSound(sound: Sound, uri: String?, volume: Float, useCustomVolume: Boolean) =
        playSound(sound, uri, volume, useCustomVolume)

    override fun stopSound() {
        mediaPlayer?.let { mediaPlayer = null; it.release() }
    }

    override fun release() = stopSound()
}
