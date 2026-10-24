package dev.flip2dnd.next.core

import android.content.Context

interface SoundPicker {
    fun launchPicker(context: Context, isDndOn: Boolean)
}
