package dev.flip2dnd.next.free

import android.content.Context
import dev.flip2dnd.next.core.PowerController

class FreePowerController(context: Context) : PowerController {
    override fun setBatterySaverEnabled(enabled: Boolean) {
        // No-Op in Free version
    }
}
