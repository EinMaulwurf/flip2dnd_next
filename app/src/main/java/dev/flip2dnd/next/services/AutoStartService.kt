package dev.flip2dnd.next.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.flip2dnd.next.diagnostics.DiagnosticLog

class AutoStartService : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        DiagnosticLog.event("auto_start_received", "reason" to intent.action)
        val prefs = context.getSharedPreferences("flip_2_dnd_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_start", true)) {
            DiagnosticLog.event("auto_start_skipped", "reason" to "auto_start_disabled")
            return
        }
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notifications.isNotificationPolicyAccessGranted) {
            DiagnosticLog.event("auto_start_skipped", "reason" to "dnd_access_missing")
            return
        }
        try {
            DetectionControl.startIfEnabled(context)
        } catch (e: IllegalStateException) {
            Log.w("AutoStartService", "Android restricted background startup; open the app to restart detection", e)
        } catch (e: SecurityException) {
            Log.w("AutoStartService", "Background startup permission is missing", e)
        }
    }
}
