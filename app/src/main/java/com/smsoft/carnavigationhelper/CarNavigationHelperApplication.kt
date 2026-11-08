package com.smsoft.carnavigationhelper

import android.app.Application
import com.smsoft.carnavigationhelper.data.sync.SyncScheduler
import com.smsoft.carnavigationhelper.service.ContentServerService
import com.smsoft.carnavigationhelper.module.dataStore
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class CarNavigationHelperApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Re-arms the automatic sync after an update (new network rule) or a restart
        CoroutineScope(Dispatchers.IO).launch {
            val enabled = dataStore.data.first()[UserPreferencesRepository.SYNC_AUTO] ?: false
            SyncScheduler.setAutomatic(this@CarNavigationHelperApplication, enabled)
            // The second sync server runs again after an update or restart if it was on
            val serverOn = dataStore.data.first()[UserPreferencesRepository.SERVER_ENABLED] ?: false
            if (serverOn) {
                try {
                    ContentServerService.start(this@CarNavigationHelperApplication)
                } catch (_: Exception) {
                    // not allowed to start in the background on this Android version: started from the settings
                }
            }
        }
    }
}
