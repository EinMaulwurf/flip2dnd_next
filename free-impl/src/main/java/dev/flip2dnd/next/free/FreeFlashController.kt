package dev.flip2dnd.next.free

import android.content.Context
import dev.flip2dnd.next.core.FlashController

class FreeFlashController(context: Context) : FlashController {
    override fun flashFlashlight(pattern: LongArray, intensity: Int) {
        // No-Op in Free version
    }

    override fun shouldSkipFeedback(isFlashlightOn: Boolean, feedbackWithFlashlightOn: Boolean): Boolean {
        return true // Always skip in Free version
    }
}
