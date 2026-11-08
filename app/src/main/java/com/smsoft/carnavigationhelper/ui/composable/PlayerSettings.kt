package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_SERVER_ENABLED
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_SERVER_KEY
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_SERVER_PORT
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_SYNC_WIFI
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_PLAYER_GAIN_DB
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_SYNC_AUTO
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_SYNC_MAX_PERCENT
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.SYNC_MAX_PERCENT_RANGE
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_SYNC_TOKEN
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_SYNC_URL
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_PLAYER_PLAYLIST_PATH
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_VOLUME_NORMALIZATION
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.PLAYER_GAIN_DB_RANGE
import com.smsoft.carnavigationhelper.ui.screen.player_settings.PlayerSettingsViewModel
import kotlin.math.roundToInt

@Composable
fun PlayerSettings(
    modifier: Modifier,
    viewModel: PlayerSettingsViewModel,
    onPickFolder: () -> Unit,
    onBlacklistAction: () -> Unit
) {
    val context = LocalContext.current

    val playlistPath by viewModel.playlistPath.collectAsStateWithLifecycle(
        initialValue = DEFAULT_PLAYER_PLAYLIST_PATH
    )
    val volumeNormalization by viewModel.volumeNormalization.collectAsStateWithLifecycle(
        initialValue = DEFAULT_VOLUME_NORMALIZATION
    )
    val playerGain by viewModel.playerGain.collectAsStateWithLifecycle(
        initialValue = DEFAULT_PLAYER_GAIN_DB
    )
    val blacklist by viewModel.blacklist.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )
    val syncUrl by viewModel.syncUrl.collectAsStateWithLifecycle(initialValue = DEFAULT_SYNC_URL)
    val syncToken by viewModel.syncToken.collectAsStateWithLifecycle(initialValue = DEFAULT_SYNC_TOKEN)
    val syncAuto by viewModel.syncAuto.collectAsStateWithLifecycle(initialValue = DEFAULT_SYNC_AUTO)
    val syncMaxPercent by viewModel.syncMaxPercent.collectAsStateWithLifecycle(
        initialValue = DEFAULT_SYNC_MAX_PERCENT
    )
    val syncWifi by viewModel.syncWifi.collectAsStateWithLifecycle(initialValue = DEFAULT_SYNC_WIFI)
    val serverEnabled by viewModel.serverEnabled.collectAsStateWithLifecycle(initialValue = DEFAULT_SERVER_ENABLED)
    val serverPort by viewModel.serverPort.collectAsStateWithLifecycle(initialValue = DEFAULT_SERVER_PORT)
    val serverKey by viewModel.serverKey.collectAsStateWithLifecycle(initialValue = DEFAULT_SERVER_KEY)

    val scrollState = rememberScrollState()
    Spacer(modifier = Modifier.height(16.dp))
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp)
    ) {
        PlayerPlaylistPath(
            modifier,
            viewModel.getPathFromUri(playlistPath),
            onPickFolder
        )
        UpdatePlayerContent(
            modifier,
            audioFilesCount = viewModel.audioFilesCount.intValue.toString(),
            audioFilesDuration = viewModel.audioFilesDuration.longValue,
            audioFilesSize = viewModel.audioFilesSize.longValue,
            isPlaylistUpdating = viewModel.isPlaylistUpdating.value,
            onClick = {
                // Without access to the folder the scan can not read it, so let the user pick it first
                if (viewModel.hasFolderAccess(context, playlistPath)) {
                    viewModel.rescanAudioFiles(context)
                } else {
                    onPickFolder()
                }
            },
            onStop = { viewModel.stopRescan() },
        )
        VolumeNormalization(
            checked = volumeNormalization,
            onCheckedChange = { viewModel.setVolumeNormalization(it) }
        )
        PlayerGain(
            gainDb = playerGain,
            onGainChange = { viewModel.setPlayerGain(it) }
        )
        Blacklist(
            count = blacklist.size,
            onClick = onBlacklistAction
        )
        ContentSync(
            url = syncUrl,
            token = syncToken,
            automatic = syncAuto,
            maxPercent = syncMaxPercent,
            onMaxPercentChange = { viewModel.saveSyncMaxPercent(it) },
            status = viewModel.syncStatus.value,
            syncing = viewModel.isSyncing.value,
            onSave = { u, t -> viewModel.saveSyncSettings(u, t) },
            onAutomaticChange = { viewModel.setAutomaticSync(context, it) },
            onSyncNow = { u, t -> viewModel.syncNow(context, u, t) },
            onStop = { viewModel.stopSync() },
            wifi = syncWifi,
            onWifiChange = { viewModel.setSyncWifi(it) },
            scanNetworks = { callback -> viewModel.loadNetworks(context, callback) },
        )
        ContentServerSection(
            enabled = serverEnabled,
            port = serverPort,
            key = serverKey,
            onEnabledChange = { viewModel.setServerEnabled(context, it) },
            onApply = { port, key -> viewModel.applyServer(context, port, key) },
        )
    }
}

@Composable
private fun VolumeNormalization(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // The whole row switches, not only the small switch (easier to hit in the car)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.volume_normalization)
            )
            Text(
                text = stringResource(R.string.volume_normalization_hint),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

// Extra gain for all songs, saved to 0.1 dB when the slider is released. It moves while dragging
@Composable
private fun PlayerGain(
    gainDb: Double,
    onGainChange: (Double) -> Unit
) {
    // Follows the stored value, but keeps the dragged value until it is saved
    val sliderValue = remember(gainDb) { mutableFloatStateOf(gainDb.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.player_gain)
            )
            Text(
                text = stringResource(R.string.gain_value, sliderValue.floatValue)
            )
        }
        Text(
            text = stringResource(R.string.player_gain_hint),
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = sliderValue.floatValue,
            onValueChange = { sliderValue.floatValue = it },
            onValueChangeFinished = {
                onGainChange((sliderValue.floatValue * 10).roundToInt() / 10.0)
            },
            valueRange = PLAYER_GAIN_DB_RANGE.start.toFloat()..PLAYER_GAIN_DB_RANGE.endInclusive.toFloat()
        )
    }
}

// Blacklisted by Next or the dislike button, the list itself is on its own screen. A rescan clears it
@Composable
private fun Blacklist(
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 16.dp, bottom = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.blacklist, count),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null
        )
    }
}

// One way sync from the add-on: new files are copied into the music folder, then the folder is rescanned.
// Address and token are saved when their field loses focus, or together with "Sync now"
@Composable
private fun ContentSync(
    url: String,
    token: String,
    automatic: Boolean,
    maxPercent: Int,
    onMaxPercentChange: (Int) -> Unit,
    status: String,
    syncing: Boolean,
    onSave: (String, String) -> Unit,
    onAutomaticChange: (Boolean) -> Unit,
    onSyncNow: (String, String) -> Unit,
    onStop: () -> Unit,
    wifi: String,
    onWifiChange: (String) -> Unit,
    scanNetworks: ((List<String>) -> Unit) -> Unit,
) {
    var urlText by remember(url) { mutableStateOf(url) }
    var tokenText by remember(token) { mutableStateOf(token) }
    // Only a value the user typed is saved, so a screen opened before the stored values load cannot blank them
    var edited by remember { mutableStateOf(false) }
    val saveIfEdited = { if (edited) onSave(urlText, tokenText) }
    // Leaving the screen saves even if a field still has focus; the latest values are read at that moment
    val latestSaveIfEdited by rememberUpdatedState(saveIfEdited)
    DisposableEffect(Unit) {
        onDispose { latestSaveIfEdited() }
    }
    var showToken by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, end = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.sync_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.sync_hint),
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it; edited = true },
            label = { Text(stringResource(R.string.sync_server)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { saveIfEdited() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .onFocusChanged { if (!it.isFocused) saveIfEdited() }
        )
        OutlinedTextField(
            value = tokenText,
            onValueChange = { tokenText = it; edited = true },
            label = { Text(stringResource(R.string.sync_token)) },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        imageVector = if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (showToken) R.string.sync_token_hide else R.string.sync_token_show
                        )
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { saveIfEdited() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .onFocusChanged { if (!it.isFocused) saveIfEdited() }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = automatic, role = Role.Switch, onValueChange = onAutomaticChange)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.sync_auto))
                Text(
                    text = stringResource(R.string.sync_auto_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = automatic,
                onCheckedChange = null
            )
        }
        WifiChoice(wifi = wifi, onWifiChange = onWifiChange, scanNetworks = scanNetworks)
        MaxSyncedShare(
            percent = maxPercent,
            onPercentChange = onMaxPercentChange
        )
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (syncing) onStop() else onSyncNow(urlText, tokenText) }
            ) {
                Text(stringResource(if (syncing) R.string.sync_stop else R.string.sync_now))
            }
            if (status.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(start = 12.dp),
                    text = status
                )
            }
        }
    }
}

// Share of the storage that synced music may use. Saved when the slider is released.
// The oldest synced files make room for new ones (FIFO). Only files the sync added are ever removed
@Composable
private fun MaxSyncedShare(
    percent: Int,
    onPercentChange: (Int) -> Unit,
) {
    val sliderValue = remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, end = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.sync_quota_title)
            )
            Text(
                text = stringResource(R.string.sync_quota_value, sliderValue.floatValue.roundToInt())
            )
        }
        Text(
            text = stringResource(R.string.sync_quota_hint),
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = sliderValue.floatValue,
            onValueChange = { sliderValue.floatValue = it },
            onValueChangeFinished = {
                onPercentChange(sliderValue.floatValue.roundToInt())
            },
            valueRange = SYNC_MAX_PERCENT_RANGE.first.toFloat()..SYNC_MAX_PERCENT_RANGE.last.toFloat()
        )
    }
}

// Choose the Wi-Fi network for automatic sync. The list shows the networks in range (needs location permission)
@Composable
private fun WifiChoice(
    wifi: String,
    onWifiChange: (String) -> Unit,
    scanNetworks: ((List<String>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var showList by remember { mutableStateOf(false) }
    var networks by remember { mutableStateOf<List<String>>(emptyList()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanNetworks { found ->
                networks = found
                showList = true
            }
        }
    }
    fun pick() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            scanNetworks { found ->
                networks = found
                showList = true
            }
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.sync_wifi_title))
            Text(
                text = if (wifi.isBlank()) stringResource(R.string.sync_wifi_any) else wifi,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.sync_wifi_hint),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Button(onClick = { pick() }) {
            Text(stringResource(R.string.sync_wifi_choose))
        }
    }

    if (showList) {
        AlertDialog(
            onDismissRequest = { showList = false },
            title = { Text(stringResource(R.string.sync_wifi_title)) },
            text = {
                if (networks.isEmpty()) {
                    Text(stringResource(R.string.sync_wifi_none))
                } else {
                    LazyColumn {
                        items(networks) { name ->
                            Text(
                                text = name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onWifiChange(name)
                                        showList = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showList = false }) {
                    Text(stringResource(R.string.sync_wifi_close))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onWifiChange("")
                    showList = false
                }) {
                    Text(stringResource(R.string.sync_wifi_any))
                }
            },
        )
    }
}

// Second sync server: serves the synced music to another system. Off by default; needs a key of at least 16 characters
@Composable
private fun ContentServerSection(
    enabled: Boolean,
    port: Int,
    key: String,
    onEnabledChange: (Boolean) -> Unit,
    onApply: (Int, String) -> Unit,
) {
    var portText by remember(port) { mutableStateOf(port.toString()) }
    var keyText by remember(key) { mutableStateOf(key) }
    var showKey by remember { mutableStateOf(false) }
    val portValue = portText.toIntOrNull()
    val portOk = portValue != null && portValue in 1024..65535
    val keyOk = keyText.trim().length >= 16
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        pendingAction?.invoke()
        pendingAction = null
    }
    // Android 13+ shows the server's notification only with permission. The server starts either way
    fun withNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingAction = action
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, end = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.server_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.server_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (on) withNotificationPermission { onEnabledChange(true) } else onEnabledChange(false)
                }
            )
        }
        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text(stringResource(R.string.server_port_label)) },
            singleLine = true,
            isError = !portOk,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        OutlinedTextField(
            value = keyText,
            onValueChange = { keyText = it },
            label = { Text(stringResource(R.string.server_key_label)) },
            singleLine = true,
            isError = !keyOk,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (showKey) R.string.sync_token_hide else R.string.sync_token_show
                        )
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        if (!keyOk) {
            Text(
                text = stringResource(R.string.server_key_hint),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Button(
            onClick = { withNotificationPermission { onApply(portValue ?: DEFAULT_SERVER_PORT, keyText.trim()) } },
            enabled = portOk && keyOk,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        ) {
            Text(stringResource(R.string.server_apply))
        }
    }
}
