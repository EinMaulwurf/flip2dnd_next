package dev.flip2dnd.next.core

import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(): Flow<List<HistoryItem>>
    suspend fun addHistory(isEnabled: Boolean, dndMode: Int)
    suspend fun clearHistory()
}
