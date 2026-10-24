package dev.flip2dnd.next.quicksettings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dev.flip2dnd.next.R
import dev.flip2dnd.next.services.FlipDetectorService
import dev.flip2dnd.next.services.DetectionControl
import dev.flip2dnd.next.widget.FlipDndWidgetProvider

private const val ACTION_TOGGLE_SERVICE = "dev.flip2dnd.next.TOGGLE_SERVICE"

@RequiresApi(Build.VERSION_CODES.N)
class FlipQuickSettingsTileService : TileService() {
    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        val toggleAction = {
            // Reuse widget's working toggle broadcast to ensure consistent behavior
            val toggleIntent = Intent(this, FlipDndWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_SERVICE
            }
            sendBroadcast(toggleIntent)

            // Keep the tile and widget in sync like the widget does
            FlipDndWidgetProvider.updateWidgetUI(this)
            // Give system a moment to apply the service state, then refresh tile
            Handler(Looper.getMainLooper()).postDelayed({ updateTileState() }, 300)
        }

        if (isLocked) {
            unlockAndRun { toggleAction() }
        } else {
            toggleAction()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val running = isServiceRunning()
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_tile_label)
        tile.icon = Icon.createWithResource(
            this,
            if (running) R.drawable.ic_widget_active else R.drawable.ic_widget_inactive
        )
        tile.updateTile()
    }

    private fun isServiceRunning(): Boolean = DetectionControl.running.value
}
