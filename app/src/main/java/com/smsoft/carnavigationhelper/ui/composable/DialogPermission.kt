package com.smsoft.carnavigationhelper.ui.composable

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
fun DialogPermission(
    message: String,
    onConfirm: () -> Unit = { },
    onDismiss: () -> Unit = { },
) {
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
            Text(text = message)
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
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

