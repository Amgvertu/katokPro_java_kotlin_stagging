package com.katok.pro.di

import android.content.Context
import com.katok.pro.network.ApiClient
import com.katok.pro.network.ApiService
import com.katok.pro.repository.*
import com.katok.pro.util.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        return ApiClient.getApiService()
    }

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager {
        return TokenManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager {
        return SessionManager(context)
    }

    @Provides
    @Singleton
    fun provideCityDetector(
        @ApplicationContext context: Context,
        locationRepository: LocationRepository
    ): CityDetector {
        return CityDetector(context, locationRepository)
    }

    @Provides
    @Singleton
    fun provideLocalDataRepository(@ApplicationContext context: Context): LocalDataRepository {
        return LocalDataRepository(context)
    }

    @Provides
    @Singleton
    fun provideLocationRepository(
        @ApplicationContext context: Context,
        localDataRepository: LocalDataRepository
    ): LocationRepository {
        return LocationRepository(context, localDataRepository)
    }

    @Provides
    @Singleton
    fun provideUserRepository(@ApplicationContext context: Context): UserRepository {
        return UserRepository(context)
    }

    @Provides
    @Singleton
    fun provideAdRepository(): AdRepository {
        return AdRepository()
    }

    @Provides
    @Singleton
    fun provideAdminRepository(): AdminRepository {
        return AdminRepository()
    }

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository {
        return AuthRepository()
    }

    @Provides
    @Singleton
    fun provideNotificationRepository(): NotificationRepository {
        return NotificationRepository()
    }

    @Provides
    @Singleton
    fun provideAdminMessageRepository(@ApplicationContext context: Context): AdminMessageRepository {
        return AdminMessageRepository(context)
    }

    @Provides
    @Singleton
    fun provideMonitoringRepository(): MonitoringRepository {
        return MonitoringRepository()
    }

    @Provides
    @Singleton
    fun provideAdvertisingRepository(): AdvertisingRepository {
        return AdvertisingRepository()
    }
}