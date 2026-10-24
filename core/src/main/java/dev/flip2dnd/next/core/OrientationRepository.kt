package dev.flip2dnd.next.core

import kotlinx.coroutines.flow.Flow

interface OrientationRepository {
    fun getOrientation(): Flow<PhoneOrientation>
    suspend fun startMonitoring()
    suspend fun stopMonitoring()
}
