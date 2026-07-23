package com.crowdmesh.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.crowdmesh.data.local.AppDatabase
import com.crowdmesh.data.local.dao.KnownPeerDao
import com.crowdmesh.data.local.dao.PresenceRecordDao
import com.crowdmesh.data.local.dao.ReceivedMessageIdDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()

    @Provides
    fun providePresenceRecordDao(database: AppDatabase): PresenceRecordDao = database.presenceRecordDao()

    @Provides
    fun provideKnownPeerDao(database: AppDatabase): KnownPeerDao = database.knownPeerDao()

    @Provides
    fun provideReceivedMessageIdDao(database: AppDatabase): ReceivedMessageIdDao = database.receivedMessageIdDao()

    @Provides
    @Singleton
    fun provideIdentityDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("crowdmesh_identity") },
        )
}
