package com.github.only52607.compose.core

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager

/**
 * Creates default [WindowManager.LayoutParams] suitable for a basic floating window.
 *
 * Sets WRAP_CONTENT dimensions, translucency, top-start gravity, default animations,
 * and flags for non-modal, non-focusable interaction.
 * Also sets the appropriate window type based on SDK version and context type.
 *
 * The created parameters include:
 * - WRAP_CONTENT dimensions for both width and height
 * - TRANSLUCENT pixel format for transparency support
 * - START|TOP gravity for positioning
 * - NOT_TOUCH_MODAL and NOT_FOCUSABLE flags
 * - Appropriate window type for overlay permissions
 *
 * @param context The context used to determine the appropriate window type.
 *                Activity contexts use default window type, while non-Activity
 *                contexts use overlay window types that require SYSTEM_ALERT_WINDOW permission.
 * @return [WindowManager.LayoutParams] configured for floating window usage.
 */
internal fun defaultLayoutParams(context: Context) = WindowManager.LayoutParams().apply {
    height = WindowManager.LayoutParams.WRAP_CONTENT
    width = WindowManager.LayoutParams.WRAP_CONTENT
    format = PixelFormat.TRANSLUCENT
    gravity = Gravity.START or Gravity.TOP
    flags =
        (
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL // Allows touches to pass through
                or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            ) // Prevents the window from taking focus (e.g., keyboard)

    // Set window type correctly for overlays
    // Requires SYSTEM_ALERT_WINDOW permission
    if (context !is Activity) {
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
    }
    // If context is an Activity, the default window type associated with the activity is used.
}
