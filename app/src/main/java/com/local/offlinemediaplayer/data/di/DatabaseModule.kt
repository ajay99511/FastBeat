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
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                "mediaplayer_db",
            )
            // v1 users now keep their data instead of being wiped on upgrade.
            .addMigrations(MIGRATION_1_5)
            // `1` must NOT be listed here. Room's Builder.build() calls
            // validateMigrationsNotRequired, which throws IllegalArgumentException when a
            // registered migration's start *or end* version also appears in this set.
            // MIGRATION_1_5 starts at 1, so listing 1 crashed the app on every launch before the
            // database was ever opened. That check is static and runs at build() time, so the
            // runtime onUpgrade/onMigrate precedence an earlier comment relied on never applied.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 3, 4)
            .build()

    @Provides
    fun provideMediaDao(database: AppDatabase): MediaDao = database.mediaDao()

    @Provides
    @Singleton
    fun provideThumbnailManager(
        @ApplicationContext context: Context,
    ): ThumbnailManager = ThumbnailManager(context)
}
