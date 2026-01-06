@file:Suppress("unused")

package com.github.only52607.compose.service

import android.content.Context
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.core.view.isNotEmpty
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.only52607.compose.core.CoreFloatingWindow
import com.github.only52607.compose.core.defaultLayoutParams
import com.github.only52607.compose.window.LocalFloatingWindow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Manages a floating window that can display Jetpack Compose content overlaying other applications.
 *
 * This class handles the lifecycle, state saving, ViewModel scope, and WindowManager interactions
 * necessary for a floating Compose UI. It implements [AutoCloseable], allowing it to be used
 * with Kotlin's `use` function for automatic resource cleanup.
 *
 * Example usage with `use`:
 * ```kotlin
 * val floatingWindow = ComposeServiceFloatingWindow(context)
 * floatingWindow.use { window -> // destroy() is called automatically at the end of this block
 *     window.setContent { /* Your Composable UI */ }
 *     window.show()
 *     // ... interact with the window ...
 * } // Window is hidden and resources are released here
 * ```
 *
 * Remember to declare the `SYSTEM_ALERT_WINDOW` permission in your AndroidManifest.xml and
 * request it at runtime if targeting Android M (API 23) or higher.
 *
 * @param context The context used for creating the window and accessing system services.
 *                An application context is preferred to avoid leaks.
 * @param windowParams The layout parameters for the floating window. Defaults are provided
 *                     by [ComposeServiceFloatingWindow.defaultLayoutParams].
 */
class ComposeServiceFloatingWindow(
    private val context: Context,
    override val windowParams: WindowManager.LayoutParams = defaultLayoutParams(context),
) : CoreFloatingWindow(
    context = context,
    windowParams = windowParams,
    tag = SERVICE_TAG,
) {
    /**
     * Sets the Jetpack Compose content for the floating window.
     *
     * This method creates a [ComposeView] and sets your [content] within it.
     * It also sets up the necessary CompositionLocal provider for [LocalFloatingWindow]
     * and connects the view to this window's lifecycle, ViewModel store, and saved state registry.
     *
     * @param content The composable function defining the UI of the floating window.
     * @throws IllegalStateException if called after [checkDestroyed] or [close] has been invoked.
     */
    fun setContent(content: @Composable () -> Unit) {
        checkDestroyed()
        Log.d(SERVICE_TAG, "Setting content.")

        disposeCompositionIfNeeded()

        val currentComposeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@ComposeServiceFloatingWindow)
            setViewTreeViewModelStoreOwner(this@ComposeServiceFloatingWindow)
            setViewTreeSavedStateRegistryOwner(this@ComposeServiceFloatingWindow)

            // Create a Recomposer tied to the window's lifecycle scope
            val recomposer = Recomposer(coroutineContext)
            compositionContext = recomposer
            parentComposition = recomposer // Store for later disposal

            // Launch the Recomposer
            lifecycleCoroutineScope.launch {
                try {
                    recomposer.runRecomposeAndApplyChanges()
                } catch (e: CancellationException) {
                    Log.d(SERVICE_TAG, "Coroutine scope cancelled normally: ${e.message}")
                } catch (e: Exception) {
                    Log.e(SERVICE_TAG, "Recomposer error", e)
                } finally {
                    Log.d(SERVICE_TAG, "Recomposer job finished.")
                }
            }

            // Set the actual Composable content
            setContent {
                CompositionLocalProvider(
                    LocalServiceFloatingWindow provides this@ComposeServiceFloatingWindow,
                ) {
                    content()
                }
            }
        }

        this.composeView = currentComposeView // Store reference

        // Replace the content view in the decorView
        if (decorView.isNotEmpty()) {
            decorView.removeAllViews()
        }

        decorView.addView(currentComposeView)

        // If already showing, update the layout immediately
        if (isShowing.value) {
            update()
        }
    }
}

private const val SERVICE_TAG = "ComposeServiceFloatingWindow"
