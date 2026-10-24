package dev.flip2dnd.next.presentation.history

data class HistorySession(
    val id: Int,
    val startTimestamp: Long,
    val endTimestamp: Long?,
    val dndMode: Int,
    val isOngoing: Boolean
) {
    val durationMillis: Long?
        get() = endTimestamp?.let { it - startTimestamp }
}