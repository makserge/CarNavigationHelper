package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.data.GeoPoint
import com.smsoft.carnavigationhelper.data.NavType
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.COUNTDOWN_TIMER_DELAY
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_NAV_TYPE
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.HOME_POSITION_LAT
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.HOME_POSITION_LONG
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.NAV_TYPE
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.WORK_POSITION_LAT
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.WORK_POSITION_LONG
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.isValidValue
import com.smsoft.carnavigationhelper.ui.screen.settings.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun Settings(
    modifier: Modifier,
    viewModel: SettingsViewModel
) {
    val scope = rememberCoroutineScope()

    // null until the stored values are loaded, the number fields show them until the user types
    val homePosition by viewModel.homePosition.collectAsStateWithLifecycle<GeoPoint?>(
        initialValue = null
    )
    val workPosition by viewModel.workPosition.collectAsStateWithLifecycle<GeoPoint?>(
        initialValue = null
    )
    val countdownTimerDelay by viewModel.countdownTimerDelay.collectAsStateWithLifecycle<Int?>(
        initialValue = null
    )
    val navType by viewModel.navType.collectAsStateWithLifecycle(
        initialValue = DEFAULT_NAV_TYPE
    )

    val fields = listOf(
        Triple(stringResource(R.string.home_lat), homePosition?.latitude?.toString(), HOME_POSITION_LAT),
        Triple(stringResource(R.string.home_long), homePosition?.longitude?.toString(), HOME_POSITION_LONG),
        Triple(stringResource(R.string.work_lat), workPosition?.latitude?.toString(), WORK_POSITION_LAT),
        Triple(stringResource(R.string.work_long), workPosition?.longitude?.toString(), WORK_POSITION_LONG),
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
            modifier = Modifier.padding(vertical = 16.dp),
            text = stringResource(R.string.nav_type),
        )
        RadioButtonGroup(
            modifier,
            options = NavType.entries.toTypedArray(),
            value = NavType.fromName(navType),
            onOptionSelected = { value ->
                scope.launch {
                    viewModel.updateField(NAV_TYPE, value.name)
                }
            },
        )
        NumberField(
            label = stringResource(R.string.countdown_timer),
            storedValue = countdownTimerDelay?.toString(),
            prefKey = COUNTDOWN_TIMER_DELAY,
            keyboardType = KeyboardType.Number,
            onValueChange = {
                scope.launch {
                    viewModel.updateField(COUNTDOWN_TIMER_DELAY, it)
                }
            },
        )
        fields.forEach { (label, value, prefKey) ->
            // Own saved state per field, the fields are created in a loop
            key(prefKey.name) {
                NumberField(
                    label = label,
                    storedValue = value,
                    prefKey = prefKey,
                    keyboardType = KeyboardType.Decimal,
                    onValueChange = {
                        scope.launch {
                            viewModel.updateField(prefKey, it)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    storedValue: String?,
    prefKey: Preferences.Key<out Any>,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
) {
    // Only the typed text is kept here, the stored value is shown until the user types. Showing the
    // stored value while typing would reformat it ("48" -> "48.0"). The repository saves only valid values
    var text by rememberSaveable { mutableStateOf<String?>(null) }
    val typed = text
    OutlinedTextField(
        modifier = Modifier.padding(top = 16.dp),
        value = typed ?: storedValue ?: "",
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = { Text(label) },
        isError = typed != null && !isValidValue(prefKey, typed),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
    )
}