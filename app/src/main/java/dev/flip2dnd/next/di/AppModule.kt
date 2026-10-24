package dev.flip2dnd.next.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.flip2dnd.next.data.local.AppDatabase
import dev.flip2dnd.next.data.local.dao.HistoryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "flip2dnd_db"
        ).build()
    }

    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    @Singleton
    fun provideSoundController(@ApplicationContext context: Context): dev.flip2dnd.next.core.SoundController {
        return dev.flip2dnd.next.core.ServiceLocator.getSoundController(context)
    }

    @Provides
    @Singleton
    fun provideFlashController(@ApplicationContext context: Context): dev.flip2dnd.next.core.FlashController {
        return dev.flip2dnd.next.core.ServiceLocator.getFlashController(context)
    }

    @Provides
    @Singleton
    fun providePowerController(@ApplicationContext context: Context): dev.flip2dnd.next.core.PowerController {
        return dev.flip2dnd.next.core.ServiceLocator.getPowerController(context)
    }

    @Provides
    @Singleton
    fun provideScheduleManager(@ApplicationContext context: Context): dev.flip2dnd.next.core.ScheduleManager {
        return dev.flip2dnd.next.core.ServiceLocator.getScheduleManager(context)
    }

    @Provides
    @Singleton
    fun provideDetectionManager(@ApplicationContext context: Context): dev.flip2dnd.next.core.DetectionManager {
        return dev.flip2dnd.next.core.ServiceLocator.getDetectionManager(context)
    }

    @Provides
    @Singleton
    fun provideSoundPicker(@ApplicationContext context: Context): dev.flip2dnd.next.core.SoundPicker {
        return dev.flip2dnd.next.core.ServiceLocator.getSoundPicker(context)
    }

    @Provides
    @Singleton
    fun provideFeatureManager(@ApplicationContext context: Context): dev.flip2dnd.next.core.ProFeatureManager {
        return dev.flip2dnd.next.core.ServiceLocator.getFeatureManager(context)
    }
}
