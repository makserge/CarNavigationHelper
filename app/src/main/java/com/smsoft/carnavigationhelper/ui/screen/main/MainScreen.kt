package com.smsoft.carnavigationhelper.ui.screen.main

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.only52607.compose.core.checkOverlayPermission
import com.github.only52607.compose.core.requestOverlayPermission
import com.google.android.gms.location.LocationServices
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.data.LocationType
import com.smsoft.carnavigationhelper.service.ButtonService
import com.smsoft.carnavigationhelper.ui.composable.ActionButtons
import com.smsoft.carnavigationhelper.ui.composable.DialogPermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isForceNavigation: Boolean,
    onPlayerAction: () -> Unit,
    onSettingsAction: () -> Unit,
    onPlayAction: () -> Unit,
) {
    val viewModel: MainViewModel = hiltViewModel()

    var showDialogOverlayPermission by rememberSaveable { mutableStateOf(false) }
    var showDialogLocationPermission by rememberSaveable { mutableStateOf(false) }

    var isLocationEnabled by rememberSaveable { mutableStateOf(false) }

    val isEnabled by ButtonService.serviceStarted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationType: LocationType by viewModel.locationType.collectAsStateWithLifecycle()
    val countdownTimer: Int by viewModel.countdownTimer.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        val overlayPermission = checkOverlayPermission(context)
        if (overlayPermission) {
            val locationPermission = viewModel.checkLocationPermission(context)
            if (locationPermission) {
                ButtonService.start(context)

                if (isForceNavigation) {
                    viewModel.startNavigationForLocation(fusedLocationClient)
                } else {
                    viewModel.launchPlayer(onPlayAction)
                }
                isLocationEnabled = true
            } else {
                showDialogLocationPermission = true
            }
        } else {
            showDialogOverlayPermission = true
        }
        onPauseOrDispose {
            showDialogOverlayPermission = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.cancelCountDownTimer()
                            onPlayerAction()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = stringResource(R.string.player)
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.cancelCountDownTimer()
                            onSettingsAction()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActionButtons(
                modifier = Modifier.fillMaxSize(),
                isEnabled && isLocationEnabled,
                locationType,
                countdownTimer,
                viewModel
            )
        }
    }
    if (showDialogOverlayPermission) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner.lifecycle) {
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    val overlayGranted = checkOverlayPermission(context)
                    if (overlayGranted) {
                        showDialogOverlayPermission = false
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        DialogPermission(
            stringResource(R.string.message_permission_to_draw_on_top_others_apps),
            onConfirm = {
                requestOverlayPermission(context)
            },
            onDismiss = {
                showDialogOverlayPermission = false
            }
        )
    }
    if (showDialogLocationPermission) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            showDialogLocationPermission = false
        }
        DialogPermission(
            stringResource(R.string.message_permission_to_get_location),
            onConfirm = {
                launcher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            },
            onDismiss = {
                showDialogLocationPermission = false
            }
        )
    }
}