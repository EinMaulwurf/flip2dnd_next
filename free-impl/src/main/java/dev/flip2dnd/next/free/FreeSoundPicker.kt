package dev.flip2dnd.next.free

import android.content.Context
import android.widget.Toast
import dev.flip2dnd.next.core.SoundPicker

class FreeSoundPicker(private val context: Context) : SoundPicker {
    override fun launchPicker(context: Context, isDndOn: Boolean) {
        // No-op for free version
        Toast.makeText(context, "Pro feature required", Toast.LENGTH_SHORT).show()
    }
}
