package com.local.offlinemediaplayer.di

import android.content.Context
import androidx.room.Room
import com.local.offlinemediaplayer.data.ThumbnailManager
import com.local.offlinemediaplayer.data.db.AppDatabase
import com.local.offlinemediaplayer.data.db.MediaDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mediaplayer_db"
        )
            .fallbackToDestructiveMigration() // FIX: Prevents crash on schema updates
            .build()
    }

    @Provides
    fun provideMediaDao(database: AppDatabase): MediaDao {
        return database.mediaDao()
    }

    @Provides
    @Singleton
    fun provideThumbnailManager(@ApplicationContext context: Context): ThumbnailManager {
        return ThumbnailManager(context)
    }
}
