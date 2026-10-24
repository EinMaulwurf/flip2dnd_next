package dev.flip2dnd.next.core

interface DetectionManager {
    fun isMediaPlaying(): Boolean
    fun areHeadphonesConnected(): Boolean
    fun isProximityCovered(): Boolean
    fun registerProximityListener(callback: (Boolean) -> Unit)
    fun unregisterProximityListener()
}
