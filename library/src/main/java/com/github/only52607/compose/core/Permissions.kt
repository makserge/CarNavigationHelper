package com.github.only52607.compose.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Requests the system overlay permission (SYSTEM_ALERT_WINDOW) from the user.
 *
 * This function opens the system settings screen where the user can grant
 * the "Draw over other apps" permission for the current application.
 * This permission is required for displaying floating windows over other applications.
 *
 * @param context The context used to start the permission request activity.
 *                Should typically be an Activity context to ensure proper navigation.
 */
fun requestOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
    )

    context.startActivity(intent)
}

/**
 * Checks if the system overlay permission (SYSTEM_ALERT_WINDOW) is currently granted.
 *
 * This function determines whether the application has permission to draw over
 * other apps, which is required for showing floating windows.
 *
 * @param context The context used to check the permission status.
 * @return `true` if the overlay permission is granted, `false` otherwise.
 */
fun checkOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}
