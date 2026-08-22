package com.local.offlinemediaplayer.data.di

import android.content.Context
import androidx.room.Room
import com.local.offlinemediaplayer.data.ThumbnailManager
import com.local.offlinemediaplayer.data.db.AppDatabase
import com.local.offlinemediaplayer.data.db.MIGRATION_1_5
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
            // v1 users now keep their data instead of being wiped on upgrade.
            .addMigrations(MIGRATION_1_5)
            // `1` is deliberately still listed here, contrary to the P3-C card, which claimed
            // leaving it makes the migration dead code. Room's RoomOpenHelper.onUpgrade and
            // RoomConnectionManager.onMigrate both read:
            //
            //     val migrations = findMigrationPath(old, new)
            //     if (migrations != null) { run them; validate }
            //     if (!migrated) { ...destructive fallback... }
            //
            // A registered migration path ALWAYS wins; this list is only consulted when no path
            // is found. So keeping `1` costs nothing while MIGRATION_1_5 exists, and if the
            // migration were ever removed or failed to resolve, v1 users would fall back to
            // today's wipe rather than a hard crash on open. That is the same trade DR-4 already
            // made deliberately: a silent wipe beats a crash loop.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)
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
