package com.smsoft.carnavigationhelper.module

import android.content.Context
import com.smsoft.carnavigationhelper.data.database.CarNavigationHelperDatabase
import com.smsoft.carnavigationhelper.data.database.repository.PlayerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class PlayerModule {
    /*
    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
        //player: ExoPlayer
    ): PlayerNotificationManager = PlayerNotificationManager(
        context = context,
        //player = player
    )

    @Provides
    @Singleton
    fun provideMediaSession(
        @ApplicationContext context: Context,
        //player: ExoPlayer
    ): MediaSession = MediaSession.Builder(context, player).build()

    @Provides
    @Singleton
    fun provideServiceHandler(
        //player: ExoPlayer
    ): RadioMediaServiceHandler = RadioMediaServiceHandler(
        //player = player
    )
*/
    @Singleton
    fun providesPlayerRepository(
        database: CarNavigationHelperDatabase,
    ) = PlayerRepository(database)
}