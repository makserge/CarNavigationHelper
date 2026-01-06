package com.smsoft.carnavigationhelper.ui.screen.settings

import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import com.smsoft.carnavigationhelper.data.GeoPoint
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    val homePosition: Flow<GeoPoint>
        get() = userPreferencesRepository.homePositionFlow

    val workPosition: Flow<GeoPoint>
        get() = userPreferencesRepository.workPositionFlow

    val countdownTimerDelay: Flow<Int>
        get() = userPreferencesRepository.countdownTimerDelayFlow

    val navType: Flow<String>
        get() = userPreferencesRepository.navTypeFlow

    suspend fun updateField(key: Preferences.Key<out Any>, value: String) {
        if (value.isNotEmpty()) {
            userPreferencesRepository.setValue(key, value)
        }
    }
}