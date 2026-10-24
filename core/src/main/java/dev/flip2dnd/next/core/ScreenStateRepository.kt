package dev.flip2dnd.next.core

import kotlinx.coroutines.flow.Flow

interface ScreenStateRepository {
    fun isScreenOff(): Flow<Boolean>
    fun startMonitoring()
    fun stopMonitoring()
}
