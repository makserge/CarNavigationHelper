package com.smsoft.carnavigationhelper.module

import android.content.Context
import androidx.room.Room
import com.smsoft.carnavigationhelper.data.database.CarNavigationHelperDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE = "carnavigationhelper_database"

    @Singleton
    @Provides
    fun providesRoomDatabase(
        @ApplicationContext context: Context
    ) = Room.databaseBuilder(
        context,
        CarNavigationHelperDatabase::class.java,
        DATABASE)
        .addMigrations(CarNavigationHelperDatabase.MIGRATION_2_3)
        // The playlist is only a scan result, so it is dropped on other schema changes and rescanned
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

        @Provides
    fun providesPlayerPlaylistDao(database: CarNavigationHelperDatabase) = database.playerPlaylistDao()
}