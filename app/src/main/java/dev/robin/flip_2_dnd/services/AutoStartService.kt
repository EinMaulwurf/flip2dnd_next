package dev.robin.flip_2_dnd.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class AutoStartService : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences("flip_2_dnd_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_start", false)) return
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notifications.isNotificationPolicyAccessGranted) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, FlipDetectorService::class.java))
        } catch (e: IllegalStateException) {
            Log.w("AutoStartService", "Android restricted background startup; open the app to restart detection", e)
        } catch (e: SecurityException) {
            Log.w("AutoStartService", "Background startup permission is missing", e)
        }
    }
}
