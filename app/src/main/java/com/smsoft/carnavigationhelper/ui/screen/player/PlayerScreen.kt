package com.smsoft.carnavigationhelper.ui.screen.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.ui.composable.DialogPermission
import com.smsoft.carnavigationhelper.ui.composable.Player

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onSettingsAction: () -> Unit,
    onPlay: () -> Unit
) {
    val viewModel: PlayerViewModel = hiltViewModel()

    val context = LocalContext.current
    var showDialogMediaPermission by rememberSaveable { mutableStateOf(false) }
    var isPlayerEnabled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val mediaPermission = viewModel.checkMediaPermission(context)
        if (mediaPermission) {
            isPlayerEnabled = true
        } else {
            showDialogMediaPermission = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
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
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isPlayerEnabled) {
                Player(
                    modifier = Modifier,
                    padding,
                    viewModel,
                    onPlaybackStarted = {
                        onPlay()
                    }
                )
            }
        }
    }
    if (showDialogMediaPermission) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ ->
            val mediaPermission = viewModel.checkMediaPermission(context)
            if (mediaPermission) {
                isPlayerEnabled = true
            } else {
                onBack()
            }
            showDialogMediaPermission = false
        }
        DialogPermission(
            stringResource(R.string.message_permission_to_read_media_files),
            onConfirm = {
                launcher.launch(viewModel.getMediaPermission())
            },
            onDismiss = {
                onBack()
            }
        )
    }
}