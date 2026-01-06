package com.github.only52607.compose.window

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal that provides access to the current [ComposeFloatingWindow] instance.
 *
 * This allows Composables within the floating window to access the window instance,
 * for example to interact with window properties, trigger updates, or access window-specific
 * functionality like drag operations.
 *
 * @throws IllegalStateException if accessed outside of a ComposeFloatingWindow context
 */
val LocalFloatingWindow: ProvidableCompositionLocal<ComposeFloatingWindow> = compositionLocalOf {
    noLocalProvidedFor("LocalFloatingWindow")
}

private fun noLocalProvidedFor(name: String): Nothing {
    error("CompositionLocal $name not present")
}
