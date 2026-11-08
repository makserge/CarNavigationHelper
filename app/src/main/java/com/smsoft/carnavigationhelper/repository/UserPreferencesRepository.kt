package com.smsoft.carnavigationhelper.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.smsoft.carnavigationhelper.data.GeoPoint
import com.smsoft.carnavigationhelper.data.NavType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
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

        // Same loudness for all tracks (one static gain per track, not compression)
        const val DEFAULT_VOLUME_NORMALIZATION = true
        val VOLUME_NORMALIZATION = booleanPreferencesKey("volume_normalization")

        // Extra gain in dB on top of the normalisation, or on top of 0 dB when it is off
        const val DEFAULT_PLAYER_GAIN_DB = 0.0
        val PLAYER_GAIN_DB = doublePreferencesKey("player_gain_db")
        val PLAYER_GAIN_DB_RANGE = -12.0..12.0

        // Content sync from the add-on (one way). The token is stored in the app's private preferences
        const val DEFAULT_SYNC_URL = ""
        val SYNC_URL = stringPreferencesKey("sync_url")
        const val DEFAULT_SYNC_TOKEN = ""
        val SYNC_TOKEN = stringPreferencesKey("sync_token")
        const val DEFAULT_SYNC_AUTO = false
        val SYNC_AUTO = booleanPreferencesKey("sync_auto")
        // Synced music may use this share of the storage; the oldest synced files make room for new ones
        const val DEFAULT_SYNC_MAX_PERCENT = 20
        val SYNC_MAX_PERCENT_RANGE = 1..90
        val SYNC_MAX_PERCENT = intPreferencesKey("sync_max_percent")
        // Names of the files the sync added (JSON list). Only these are ever removed by the sync
        val SYNC_TRACKED = stringPreferencesKey("sync_tracked")
        // Automatic sync only runs on this exact Wi-Fi name; empty = any Wi-Fi
        const val DEFAULT_SYNC_WIFI = ""
        val SYNC_WIFI = stringPreferencesKey("sync_wifi_ssid")
        // Second sync server (the car app serves its synced music to another system)
        const val DEFAULT_SERVER_ENABLED = false
        val SERVER_ENABLED = booleanPreferencesKey("server_enabled")
        const val DEFAULT_SERVER_PORT = 47831
        val SERVER_PORT = intPreferencesKey("server_port")
        const val DEFAULT_SERVER_KEY = ""
        val SERVER_KEY = stringPreferencesKey("server_key")

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

    val volumeNormalizationFlow: Flow<Boolean> = preferencesFlow
        .map { preferences ->
            preferences[VOLUME_NORMALIZATION] ?: DEFAULT_VOLUME_NORMALIZATION
        }

    val playerGainFlow: Flow<Double> = preferencesFlow
        .map { preferences ->
            preferences[PLAYER_GAIN_DB] ?: DEFAULT_PLAYER_GAIN_DB
        }

    val syncUrlFlow: Flow<String> = preferencesFlow
        .map { preferences ->
            preferences[SYNC_URL] ?: DEFAULT_SYNC_URL
        }

    val syncTokenFlow: Flow<String> = preferencesFlow
        .map { preferences ->
            preferences[SYNC_TOKEN] ?: DEFAULT_SYNC_TOKEN
        }

    val syncAutoFlow: Flow<Boolean> = preferencesFlow
        .map { preferences -> preferences[SYNC_AUTO] ?: DEFAULT_SYNC_AUTO }

    val syncWifiFlow: Flow<String> = preferencesFlow
        .map { preferences -> preferences[SYNC_WIFI] ?: DEFAULT_SYNC_WIFI }

    val serverEnabledFlow: Flow<Boolean> = preferencesFlow
        .map { preferences -> preferences[SERVER_ENABLED] ?: DEFAULT_SERVER_ENABLED }

    val serverPortFlow: Flow<Int> = preferencesFlow
        .map { preferences -> preferences[SERVER_PORT] ?: DEFAULT_SERVER_PORT }

    val serverKeyFlow: Flow<String> = preferencesFlow
        .map { preferences -> preferences[SERVER_KEY] ?: DEFAULT_SERVER_KEY }

    val syncMaxPercentFlow: Flow<Int> = preferencesFlow
        .map { preferences -> preferences[SYNC_MAX_PERCENT] ?: DEFAULT_SYNC_MAX_PERCENT }

    val syncTrackedFlow: Flow<Set<String>> = preferencesFlow
        .map { preferences ->
            val raw = preferences[SYNC_TRACKED] ?: return@map emptySet<String>()
            runCatching { Json.decodeFromString(ListSerializer(String.serializer()), raw) }
                .getOrDefault(emptyList())
                .toSet()
        }

    suspend fun setButtonPosition(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[BUTTON_POSITION_X] = x
            preferences[BUTTON_POSITION_Y] = y
        }
    }

    suspend fun setVolumeNormalization(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[VOLUME_NORMALIZATION] = enabled
        }
    }

    suspend fun setPlayerGain(gainDb: Double) {
        dataStore.edit { preferences ->
            preferences[PLAYER_GAIN_DB] = gainDb.coerceIn(PLAYER_GAIN_DB_RANGE)
        }
    }

    suspend fun setSyncSettings(url: String, token: String) {
        dataStore.edit { preferences ->
            preferences[SYNC_URL] = url.trim()
            preferences[SYNC_TOKEN] = token.trim()
        }
    }

    suspend fun setSyncWifi(ssid: String) {
        dataStore.edit { preferences -> preferences[SYNC_WIFI] = ssid.trim() }
    }

    suspend fun setServerEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[SERVER_ENABLED] = enabled }
    }

    suspend fun setServer(port: Int, key: String) {
        dataStore.edit { preferences ->
            preferences[SERVER_PORT] = port.coerceIn(1024, 65535)
            preferences[SERVER_KEY] = key.trim()
        }
    }

    suspend fun setSyncMaxPercent(percent: Int) {
        dataStore.edit { preferences ->
            preferences[SYNC_MAX_PERCENT] = percent.coerceIn(SYNC_MAX_PERCENT_RANGE)
        }
    }

    suspend fun setSyncTracked(names: Set<String>) {
        dataStore.edit { preferences ->
            preferences[SYNC_TRACKED] = Json.encodeToString(ListSerializer(String.serializer()), names.sorted())
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SYNC_AUTO] = enabled
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