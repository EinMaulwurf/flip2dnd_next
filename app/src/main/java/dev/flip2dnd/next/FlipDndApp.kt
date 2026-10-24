package dev.flip2dnd.next

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FlipDndApp : Application() {
	override fun onCreate() {
        super.onCreate()
        dev.flip2dnd.next.diagnostics.DiagnosticLog.get(this)
        val prefs = getSharedPreferences("flip_2_dnd_settings", MODE_PRIVATE)
        if (!prefs.getBoolean("personal_autostart_default_v2", false)) {
            prefs.edit().putBoolean("auto_start", true)
                .putBoolean("personal_autostart_default_v2", true).commit()
        }
	}
}
