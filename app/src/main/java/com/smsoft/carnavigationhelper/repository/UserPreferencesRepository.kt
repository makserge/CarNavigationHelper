package com.smsoft.carnavigationhelper.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.smsoft.carnavigationhelper.data.GeoPoint
import com.smsoft.carnavigationhelper.data.NavType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private const val DEFAULT_BUTTON_POSITION_X = 900
        private const val DEFAULT_BUTTON_POSITION_Y = 100
        private val BUTTON_POSITION_X = intPreferencesKey("button_position_x")
        private val BUTTON_POSITION_Y = intPreferencesKey("button_position_y")

        const val DEFAULT_PLAYER_PLAYLIST_PATH = "content://com.android.externalstorage.documents/tree/primary:Music"
        val PLAYER_PLAYLIST_PATH = stringPreferencesKey("player_playlist_path")

        const val DEFAULT_HOME_POSITION_LAT = 49.309757
        const val DEFAULT_HOME_POSITION_LONG = 12.077504
        val HOME_POSITION_LAT = doublePreferencesKey("home_position_lat")
        val HOME_POSITION_LONG = doublePreferencesKey("home_position_long")

        const val DEFAULT_WORK_POSITION_LAT = 49.418024
        const val DEFAULT_WORK_POSITION_LONG = 11.646501
        val WORK_POSITION_LAT = doublePreferencesKey("work_position_lat")
        val WORK_POSITION_LONG = doublePreferencesKey("work_position_long")

        const val DEFAULT_COUNTDOWN_TIMER_DELAY = 15
        val COUNTDOWN_TIMER_DELAY = intPreferencesKey("countdown_timer_delay")
        val COUNTDOWN_TIMER_DELAY_RANGE = 0..600

        val DEFAULT_NAV_TYPE = NavType.WAZE.name
        val NAV_TYPE = stringPreferencesKey("nav_type")

        // Number settings are typed as text, only values that parse and are in range may be saved
        fun isValidValue(key: Preferences.Key<out Any>, value: String): Boolean = when (key) {
            HOME_POSITION_LAT, WORK_POSITION_LAT -> value.toDoubleOrNull()?.let { it in -90.0..90.0 } == true
            HOME_POSITION_LONG, WORK_POSITION_LONG -> value.toDoubleOrNull()?.let { it in -180.0..180.0 } == true
            COUNTDOWN_TIMER_DELAY -> value.toIntOrNull()?.let { it in COUNTDOWN_TIMER_DELAY_RANGE } == true
            else -> true
        }
    }

    // A read error falls back to the defaults instead of crashing every collector
    private val preferencesFlow: Flow<Preferences> = dataStore
        .data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }

    val buttonPositionFlow: Flow<Pair<Int, Int>> = preferencesFlow
        .map { preferences ->
            val x = preferences[BUTTON_POSITION_X] ?: DEFAULT_BUTTON_POSITION_X
            val y = preferences[BUTTON_POSITION_Y] ?: DEFAULT_BUTTON_POSITION_Y
            Pair(x, y)
        }

    val playerPlaylistPathFlow: Flow<String> = preferencesFlow
        .map { preferences ->
            preferences[PLAYER_PLAYLIST_PATH] ?: DEFAULT_PLAYER_PLAYLIST_PATH
        }

    val homePositionFlow: Flow<GeoPoint> = preferencesFlow
        .map { preferences ->
            val lat = preferences[HOME_POSITION_LAT] ?: DEFAULT_HOME_POSITION_LAT
            val long = preferences[HOME_POSITION_LONG] ?: DEFAULT_HOME_POSITION_LONG
            GeoPoint(lat, long)
        }

    val workPositionFlow: Flow<GeoPoint> = preferencesFlow
        .map { preferences ->
            val lat = preferences[WORK_POSITION_LAT] ?: DEFAULT_WORK_POSITION_LAT
            val long = preferences[WORK_POSITION_LONG] ?: DEFAULT_WORK_POSITION_LONG
            GeoPoint(lat, long)
        }

    val countdownTimerDelayFlow: Flow<Int> = preferencesFlow
        .map { preferences ->
            preferences[COUNTDOWN_TIMER_DELAY] ?: DEFAULT_COUNTDOWN_TIMER_DELAY
        }

    val navTypeFlow: Flow<String> = preferencesFlow
        .map { preferences ->
            preferences[NAV_TYPE] ?: DEFAULT_NAV_TYPE
        }

    suspend fun setButtonPosition(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[BUTTON_POSITION_X] = x
            preferences[BUTTON_POSITION_Y] = y
        }
    }

    suspend fun setValue(key: Preferences.Key<out Any>, value: String) {
        // Half-typed or out of range numbers are ignored, so toDouble()/toInt() below can not fail
        if (!isValidValue(key, value)) return
        dataStore.edit { preferences ->
            when (key) {
                HOME_POSITION_LAT -> {
                    preferences[HOME_POSITION_LAT] = value.toDouble()
                }
                HOME_POSITION_LONG -> {
                    preferences[HOME_POSITION_LONG] = value.toDouble()
                }
                WORK_POSITION_LAT -> {
                    preferences[WORK_POSITION_LAT] = value.toDouble()
                }
                WORK_POSITION_LONG -> {
                    preferences[WORK_POSITION_LONG] = value.toDouble()
                }
                COUNTDOWN_TIMER_DELAY -> {
                    preferences[COUNTDOWN_TIMER_DELAY] = value.toInt()
                }
                NAV_TYPE -> {
                    preferences[NAV_TYPE] = value
                }
                PLAYER_PLAYLIST_PATH -> {
                    preferences[PLAYER_PLAYLIST_PATH] = value
                }
            }
        }
    }
}