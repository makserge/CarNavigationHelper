package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.only52607.compose.core.checkOverlayPermission
import com.github.only52607.compose.core.requestOverlayPermission
import com.smsoft.carnavigationhelper.R

@Composable
fun DialogOverlayPermission(
    onDismiss: () -> Unit = { },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                val overlayGranted = checkOverlayPermission(context)
                if (overlayGranted) {
                    onDismiss()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
            Text(text = stringResource(R.string.message_permission_to_draw_on_top_others_apps))
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    requestOverlayPermission(context)
                },
            ) {
                Text(stringResource(R.string.grant_permission))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

