package com.smsoft.carnavigationhelper.ui.composable

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.smsoft.carnavigationhelper.R

@Composable
fun DialogLocationPermission(
    onDismiss: () -> Unit = { },
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onDismiss()
    }

    AlertDialog(
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = stringResource(R.string.permission_required),
            )
        },
        title = {
            Text(text = stringResource(id = R.string.permission_required))
        },
        text = {
            Text(text = stringResource(R.string.message_permission_to_get_location))
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    launcher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                },
            ) {
                Text(stringResource(R.string.grant_permission))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}