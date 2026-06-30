package com.nick.telegramalarm.di

import com.nick.telegramalarm.data.backend.BackendRepository
import com.nick.telegramalarm.data.backend.BackendRepositoryImpl
import com.nick.telegramalarm.data.history.AlarmHistoryRepository
import com.nick.telegramalarm.data.history.AlarmHistoryRepositoryImpl
import com.nick.telegramalarm.data.groups.GroupsCacheRepository
import com.nick.telegramalarm.data.groups.GroupsCacheRepositoryImpl
import com.nick.telegramalarm.data.people.PeopleCacheRepository
import com.nick.telegramalarm.data.people.PeopleCacheRepositoryImpl
import com.nick.telegramalarm.data.settings.SettingsRepository
import com.nick.telegramalarm.data.settings.SettingsRepositoryImpl
import com.nick.telegramalarm.domain.AlarmController
import com.nick.telegramalarm.domain.AlarmControllerImpl
import com.nick.telegramalarm.network.AlarmWebSocketClient
import com.nick.telegramalarm.network.OkHttpAlarmWebSocketClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAlarmHistoryRepository(impl: AlarmHistoryRepositoryImpl): AlarmHistoryRepository

    @Binds
    @Singleton
    abstract fun bindBackendRepository(impl: BackendRepositoryImpl): BackendRepository

    @Binds
    @Singleton
    abstract fun bindPeopleCacheRepository(impl: PeopleCacheRepositoryImpl): PeopleCacheRepository

    @Binds
    @Singleton
    abstract fun bindGroupsCacheRepository(impl: GroupsCacheRepositoryImpl): GroupsCacheRepository

    @Binds
    @Singleton
    abstract fun bindWebSocketClient(impl: OkHttpAlarmWebSocketClient): AlarmWebSocketClient

    @Binds
    @Singleton
    abstract fun bindAlarmController(impl: AlarmControllerImpl): AlarmController

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
