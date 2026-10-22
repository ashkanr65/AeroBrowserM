package com.aero.browser.core.di

import android.content.Context
import com.aero.browser.core.network.HttpsEnforcer
import com.aero.browser.core.network.TrackingProtection
import com.aero.browser.core.storage.AppDatabase
import com.aero.browser.core.storage.HomepageSource
import com.aero.browser.core.storage.PreferencesHomepageSource
import com.aero.browser.core.storage.SecurePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Process-wide singletons. Everything the web engine and storage layers
 * depend on is wired here so activities/fragments can just inject interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideTrackingProtection(): TrackingProtection = TrackingProtection.forApp()

    @Provides
    @Singleton
    fun providesHttpsEnforcer(): HttpsEnforcer = HttpsEnforcer

    @Provides
    @Singleton
    fun provideSecurePreferences(@ApplicationContext context: Context): SecurePreferences =
        SecurePreferences.get(context)

    @Provides
    @Singleton
    fun provideHomepageSource(preferences: SecurePreferences): HomepageSource =
        PreferencesHomepageSource(preferences)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.get(context)

    @Provides
    @Singleton
    fun provideBookmarkDao(database: AppDatabase) = database.bookmarkDao()

    @Provides
    @Singleton
    fun provideHistoryDao(database: AppDatabase) = database.historyDao()
}
