package dev.flip2dnd.next.di

import dev.flip2dnd.next.data.repository.DndRepositoryImpl
import dev.flip2dnd.next.data.repository.FeedbackRepositoryImpl
import dev.flip2dnd.next.data.repository.HistoryRepositoryImpl
import dev.flip2dnd.next.data.repository.OrientationRepositoryImpl
import dev.flip2dnd.next.data.repository.ScreenStateRepositoryImpl
import dev.flip2dnd.next.data.repository.SettingsRepositoryImpl
import dev.flip2dnd.next.core.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDndRepository(
        dndRepositoryImpl: DndRepositoryImpl
    ): DndRepository

    @Binds
    @Singleton
    abstract fun bindScreenStateRepository(
        screenStateRepositoryImpl: ScreenStateRepositoryImpl
    ): ScreenStateRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindOrientationRepository(
        orientationRepositoryImpl: OrientationRepositoryImpl
    ): OrientationRepository

    @Binds
    @Singleton
    abstract fun bindFeedbackRepository(
        feedbackRepositoryImpl: FeedbackRepositoryImpl
    ): FeedbackRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        historyRepositoryImpl: HistoryRepositoryImpl
    ): HistoryRepository
}
