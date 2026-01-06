package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.data.GeoPoint
import com.smsoft.carnavigationhelper.data.NavType
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.COUNTDOWN_TIMER_DELAY
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_COUNTDOWN_TIMER_DELAY
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_HOME_POSITION_LAT
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_HOME_POSITION_LONG
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_NAV_TYPE
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_WORK_POSITION_LAT
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_WORK_POSITION_LONG
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.HOME_POSITION_LAT
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.HOME_POSITION_LONG
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.NAV_TYPE
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.WORK_POSITION_LAT
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.WORK_POSITION_LONG
import com.smsoft.carnavigationhelper.ui.screen.settings.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun Settings(
    modifier: Modifier,
    viewModel: SettingsViewModel
) {
    val scope = rememberCoroutineScope()

    val homePosition by viewModel.homePosition.collectAsStateWithLifecycle(
        initialValue = GeoPoint(DEFAULT_HOME_POSITION_LAT, DEFAULT_HOME_POSITION_LONG)
    )
    val workPosition by viewModel.workPosition.collectAsStateWithLifecycle(
        initialValue = GeoPoint(DEFAULT_WORK_POSITION_LAT, DEFAULT_WORK_POSITION_LONG)
    )
    val countdownTimerDelay by viewModel.countdownTimerDelay.collectAsStateWithLifecycle(
        initialValue = DEFAULT_COUNTDOWN_TIMER_DELAY
    )
    val navType by viewModel.navType.collectAsStateWithLifecycle(
        initialValue = DEFAULT_NAV_TYPE
    )
    val fields = listOf(
        Triple(stringResource(R.string.home_lat), homePosition.latitude.toString(), HOME_POSITION_LAT),
        Triple(stringResource(R.string.home_long), homePosition.longitude.toString(), HOME_POSITION_LONG),
        Triple(stringResource(R.string.work_lat), workPosition.latitude.toString(), WORK_POSITION_LAT),
        Triple(stringResource(R.string.work_long), workPosition.longitude.toString(), WORK_POSITION_LONG),
    )
    val scrollState = rememberScrollState()
    Spacer(modifier = Modifier.height(16.dp))
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_type),
            modifier = Modifier.padding(vertical = 16.dp),
        )
        EnumRadioButtonGroup(
            value = NavType.fromName(navType),
            onOptionSelected = { value ->
                scope.launch {
                    viewModel.updateField(NAV_TYPE, value.name)
                }
            },
        )
        OutlinedTextField(
            modifier = Modifier.padding(top = 16.dp),
            value = countdownTimerDelay.toString(),
            onValueChange = {
                scope.launch {
                    viewModel.updateField(COUNTDOWN_TIMER_DELAY, it)
                }
            },
            label = { Text(stringResource(R.string.countdown_timer)) }
        )
        fields.forEach { (label, value, key) ->
            OutlinedTextField(
                modifier = Modifier.padding(top = 16.dp),
                value = value,
                onValueChange = {
                    scope.launch {
                        viewModel.updateField(key, it)
                    }
                },
                label = { Text(label) }
            )
        }
    }
}