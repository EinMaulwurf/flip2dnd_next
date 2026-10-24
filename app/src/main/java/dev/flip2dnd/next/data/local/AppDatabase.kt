package dev.flip2dnd.next.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.flip2dnd.next.data.local.dao.HistoryDao
import dev.flip2dnd.next.data.local.entity.HistoryEntity

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
