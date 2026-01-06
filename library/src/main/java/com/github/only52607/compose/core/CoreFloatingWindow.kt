package com.github.only52607.compose.core

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

open class CoreFloatingWindow(
    private val context: Context,
    open val windowParams: WindowManager.LayoutParams,
    private val tag: String = "CoreFloatingWindow",
) : SavedStateRegistryOwner,
    ViewModelStoreOwner,
    AutoCloseable {

    // --- Lifecycle, ViewModel, SavedState ---

    // Use a SupervisorJob so failure of one child doesn't cause others to fail
    // Use a custom scope tied to the window's lifecycle for managing window-specific coroutines
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(tag, "Coroutine Exception: ${throwable.message}", throwable)
    }
    internal val coroutineContext = AndroidUiDispatcher.CurrentThread
    internal val lifecycleCoroutineScope = CoroutineScope(
        SupervisorJob() +
            coroutineContext + coroutineExceptionHandler,
    )
    private val mutex = Mutex()

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val savedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // --- Window State ---

    private val _isShowing = MutableStateFlow(false)

    /**
     * A [StateFlow] indicating whether the floating window is currently shown (`true`) or hidden (`false`).
     * Does not reflect the destroyed state. Check [isDestroyed] for that.
     */
    val isShowing: StateFlow<Boolean>
        get() = _isShowing.asStateFlow()

    private val _isDestroyed = MutableStateFlow(false)

    /**
     * A [StateFlow] indicating whether the floating window has been destroyed (`true`).
     * Once destroyed, the instance cannot be reused. Create a new instance if needed.
     */
    val isDestroyed: StateFlow<Boolean>
        get() = _isDestroyed.asStateFlow()

    /**
     * The root view container for the floating window's content.
     * This is the view added to the WindowManager.
     */
    var decorView: ViewGroup = FrameLayout(context)
        .apply {
            // Important: Prevent clipping so shadows or elements outside bounds can be drawn
            clipChildren = false
            clipToPadding = false
        }
        private set

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /**
     * Helper class providing access to the display metrics for the floating window.
     *
     * Used internally to calculate maximum coordinates and display dimensions
     * for proper window positioning and bounds checking.
     */
    val display = DisplayHelper(context, windowManager)
    internal var composeView: ComposeView? = null // Hold a direct reference
    internal var parentComposition: Recomposer? = null // Hold reference for disposal

    /**
     * The maximum X coordinate for the floating window.
     *
     * This represents the rightmost position where the window can be placed
     * while still remaining fully visible on screen. Calculated as the
     * screen width minus the window's measured width.
     *
     * @return The maximum X coordinate in pixels, or 0 if the window hasn't been measured yet.
     */
    val maxXCoordinate
        get() = display.metrics.widthPixels - decorView.measuredWidth

    /**
     * The maximum Y coordinate for the floating window.
     *
     * This represents the bottommost position where the window can be placed
     * while still remaining fully visible on screen. Calculated as the
     * screen height minus the window's measured height.
     *
     * @return The maximum Y coordinate in pixels, or 0 if the window hasn't been measured yet.
     */
    val maxYCoordinate
        get() = display.metrics.heightPixels - decorView.measuredHeight

    /**
     * Shows the floating window with a fade-in animation.
     *
     * Adds the [decorView] to the [WindowManager] using the configured [windowParams].
     * Moves the lifecycle state to STARTED.
     * Requires the `SYSTEM_ALERT_WINDOW` permission.
     *
     * @throws IllegalStateException if the window is already destroyed ([isDestroyed] is true).
     * @throws SecurityException if the `SYSTEM_ALERT_WINDOW` permission is not granted (logged as warning).
     */
    fun show() {
        checkDestroyed()

        require(composeView != null) {
            "Content must be set using setContent() before showing the window."
        }

        if (!isAvailable()) {
            Log.w(
                tag,
                "Overlay permission (SYSTEM_ALERT_WINDOW) not granted. Cannot show window.",
            )
            return
        }

        if (_isShowing.value) {
            Log.d(tag, "Window already showing, updating layout.")
            update() // Ensure layout is up-to-date if show is called again
            return
        }

        Log.d(tag, "Showing window.")

        try {
            // Ensure the view doesn't have a parent before adding
            if (decorView.parent != null) {
                Log.w(tag, "DecorView already has a parent. Removing it.")
                (decorView.parent as? ViewGroup)?.removeView(decorView)
            }
            // Set initial alpha to 0 for fade-in animation
            decorView.alpha = INVISIBLE_ALPHA
            windowManager.addView(decorView, windowParams)
            // Animate fade-in
            decorView.animate()
                .alpha(VISIBLE_ALPHA)
                .setDuration(ANIMATION_DURATION)
                .withEndAction {
                    // Move lifecycle to STARTED only after view is successfully added
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                }
                .start()
            // Update state last
            _isShowing.update { true }
        } catch (e: Exception) {
            // Catch potential exceptions from WindowManager (e.g., security, bad token)
            Log.e(tag, "Error showing window: ${e.message}", e)
            // Reset state if adding failed
            _isShowing.update { false }
        }
    }

    /**
     * Updates the window coordinates to the specified position.
     *
     * This method updates the window parameters with new coordinates and followed
     * by a call to [update] to apply the changes to the displayed window.
     *
     * @param left The new X coordinate (left position) for the window.
     * @param top The new Y coordinate (top position) for the window.
     */
    fun updateCoordinate(left: Int, top: Int) {
        windowParams.x = left
        windowParams.y = top

        try {
            update()
        } catch (e: Exception) {
            // Log but don't crash on update failures during drag
            Log.w(tag, "Failed to update window position: ${e.message}")
        }
    }

    /**
     * Updates the layout of the floating window using the current [windowParams].
     * Call this after modifying [windowParams] (e.g., position or size) while the window is showing.
     *
     * @throws IllegalStateException if the window is already destroyed ([isDestroyed] is true).
     */
    fun update() = lifecycleCoroutineScope.launch {
        checkDestroyed()

        if (!_isShowing.value) {
            Log.w(tag, "Update called but window is not showing.")
            return@launch
        }
        Log.d(tag, "Updating window layout.")
        mutex.withLock {
            try {
                windowManager.updateViewLayout(decorView, windowParams)
            } catch (e: Exception) {
                Log.e(tag, "Error updating window layout: ${e.message}", e)
            }
        }
    }

    /**
     * Hides the floating window with fade-out animation.
     *
     * Removes the [decorView] from the [WindowManager].
     * Moves the lifecycle state to STOPPED.
     *
     * @throws IllegalStateException if the window is already destroyed ([isDestroyed] is true).
     */
    fun hide() {
        checkDestroyed()

        if (!_isShowing.value) {
            Log.d(tag, "Hide called but window is already hidden.")
            return
        }
        Log.d(tag, "Hiding window.")

        _isShowing.update { false }
        try {
            // Check if view is still attached before animating removal
            if (decorView.parent != null) {
                // Animate fade-out
                decorView.animate()
                    .alpha(INVISIBLE_ALPHA)
                    .setDuration(ANIMATION_DURATION)
                    .withEndAction {
                        // Remove view after animation
                        try {
                            windowManager.removeViewImmediate(decorView)
                        } catch (e: Exception) {
                            Log.e(tag, "Error removing window: ${e.message}", e)
                        }
                    }
                    .start()
            } else {
                Log.w(tag, "Hide called but DecorView has no parent.")
            }
        } catch (e: Exception) {
            // Catch potential exceptions (e.g., view not attached)
            Log.e(tag, "Error hiding window: ${e.message}", e)
        } finally {
            // Move lifecycle to STOPPED regardless of removal success,
            // as the intention is to stop interaction.
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    /**
     * Checks if the overlay permission is available for displaying floating windows.
     *
     * This method checks whether the application has the SYSTEM_ALERT_WINDOW permission
     * required to display floating windows over other applications. On Android M (API 23)
     * and above, this permission must be explicitly granted by the user.
     *
     * @return `true` if the overlay permission is granted, `false` otherwise.
     * @see requestOverlayPermission for requesting the permission if not available.
     */
    fun isAvailable(): Boolean = Settings.canDrawOverlays(context)

    init {
        // Warn if non-application context is used to prevent memory leaks
        if (context !is Application && context.applicationContext != context) {
            Log.w(
                tag,
                "Consider using applicationContext " +
                    "instead of activity context to prevent memory leaks",
            )
        }
        // Restore state early in the lifecycle
        savedStateRegistryController.performRestore(null)
        // Mark the lifecycle as CREATED
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        // Enable SavedStateHandles for ViewModels
        enableSavedStateHandles()
        Log.d(tag, "FloatingWindow initialized.")
    }

    /** Throws an [IllegalStateException] if the window has been destroyed. */
    internal fun checkDestroyed() {
        check(!_isDestroyed.value) {
            "FloatingWindow has been destroyed and cannot be used."
        }
    }

    /** Disposes the Compose composition and clears the reference. */
    internal fun disposeCompositionIfNeeded() {
        composeView?.let {
            Log.d(tag, "Disposing composition.")
            it.disposeComposition() // Dispose the underlying composition
            parentComposition?.cancel() // Cancel the recomposer explicitly if needed
            parentComposition = null
            decorView.removeView(it) // Remove view from hierarchy
            composeView = null // Clear the reference
        }
    }

    /**
     * Implementation of [AutoCloseable].
     * Destroys the floating window, releasing all associated resources.
     *
     * This performs the following actions:
     * 1. Hides the window if it is currently showing.
     * 2. Disposes the Jetpack Compose composition.
     * 3. Cancels all coroutines launched in the window's lifecycle scope.
     * 4. Moves the lifecycle state to DESTROYED.
     * 5. Clears the [ViewModelStore], destroying associated ViewModels.
     * 6. Cleans up internal references.
     *
     * **Once destroyed, this instance cannot be reused.** Create a new `FloatingWindow`
     * instance if you need to show a floating window again.
     */
    override fun close() {
        if (_isDestroyed.value) {
            Log.w(tag, "Destroy called but window is already destroyed.")
            return
        }
        Log.d(tag, "Destroying window...")

        // Hide the window if showing (ensures view is removed from WindowManager)
        if (_isShowing.value) {
            try {
                hide()
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Error hiding window during destruction: ${e.message}",
                    e,
                )
            }
        }

        // Mark as destroyed immediately to prevent race conditions
        _isDestroyed.update { true }

        // Dispose the composition
        disposeCompositionIfNeeded()

        // Cancel the custom lifecycle scope and its children (including the Recomposer's job)
        Log.d(tag, "Cancelling lifecycle coroutine scope.")
        try { // Explicit cancellation
            lifecycleCoroutineScope.cancel(
                CancellationException("FloatingWindow destroyed"),
            )
        } catch (e: CancellationException) {
            Log.d(tag, "Coroutine scope cancelled normally: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "Recomposer error", e)
        }

        // Move lifecycle to DESTROYED
        Log.d(tag, "Setting lifecycle state to DESTROYED.")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        // Clear the ViewModelStore
        Log.d(tag, "Clearing ViewModelStore.")
        viewModelStore.clear()

        // Clean up references
        // decorView is managed by GC once this instance is gone.
        // composeView reference is cleared in disposeCompositionIfNeeded
        // windowManager is a system service, no need to clear.
        // savedStateRegistryController is tied to the lifecycle/owner, should be handled.

        Log.d(tag, "FloatingWindow destroyed successfully.")
    }

    companion object {
        /**
         * Duration in milliseconds for fade in/out animations when showing/hiding the window.
         */
        private const val ANIMATION_DURATION = 300L

        /**
         * Alpha value representing fully invisible state.
         */
        private const val INVISIBLE_ALPHA = 0f

        /**
         * Alpha value representing fully visible state.
         */
        private const val VISIBLE_ALPHA = 1f
    }
}
