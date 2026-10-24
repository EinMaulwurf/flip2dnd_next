package dev.flip2dnd.next.core

import kotlinx.coroutines.flow.Flow

interface DndRepository {
    fun isActivated(): Flow<Boolean>
    fun isOwned(): Flow<Boolean>
    suspend fun refresh()
    fun getDndMode(): Flow<Int>
    suspend fun setActivated(enabled: Boolean)
    suspend fun toggle()
    fun onCleared()
}
