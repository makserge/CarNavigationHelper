package com.smsoft.carnavigationhelper.ui.floating

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository
import com.smsoft.carnavigationhelper.service.ButtonService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class FloatingViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val location: Flow<Pair<Int, Int>>
        get() = userPreferencesRepository.buttonPositionFlow

    private val _navigateToNext = MutableSharedFlow<Boolean>()
    val navigateToNext = _navigateToNext.asSharedFlow()

    fun updateLocation(x: Int, y: Int) = viewModelScope.launch {
        userPreferencesRepository.setButtonPosition(x, y)
    }

    suspend fun showApp(context: Context) {
        _navigateToNext.emit(true)
        ButtonService.hideButton(context)
    }
}
