package com.github.only52607.compose.core

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Helper class for retrieving display metrics information for floating windows.
 *
 * This class provides a unified way to get display metrics across different Android versions,
 * handling the API changes introduced in Android API 34 (UPSIDE_DOWN_CAKE) where the
 * deprecated [WindowManager.getDefaultDisplay] method was replaced with
 * [WindowManager.getCurrentWindowMetrics].
 *
 * @param context The context used to access display information and resources.
 * @param windowManager The WindowManager instance used to retrieve window metrics.
 */
class DisplayHelper(
    private val context: Context,
    private val windowManager: WindowManager,
) {

    /**
     * Gets the current display metrics for the floating window.
     *
     * On Android API 34 and above, uses [WindowManager.getCurrentWindowMetrics] to get
     * the current window bounds and derives metrics from those bounds combined with
     * density information from the context.
     *
     * On older Android versions, uses the deprecated [WindowManager.getDefaultDisplay]
     * method to get real display metrics.
     *
     * @return [DisplayMetrics] containing width, height, density, and densityDpi information.
     */
    val metrics: DisplayMetrics
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val bounds = windowManager.currentWindowMetrics.bounds
            val contextMetrics = context.resources.displayMetrics

            val defaultMetrics = DisplayMetrics().apply {
                widthPixels = bounds.width()
                heightPixels = bounds.height()
                density = contextMetrics.density
                densityDpi = contextMetrics.densityDpi
            }

            defaultMetrics
        } else {
            DisplayMetrics().also {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(it)
            }
        }
}
