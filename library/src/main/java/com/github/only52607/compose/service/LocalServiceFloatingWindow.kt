package com.github.only52607.compose.service

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal that provides access to the current [ComposeServiceFloatingWindow] instance.
 *
 * This allows Composables within the floating window to access the window instance,
 * for example to interact with window properties, trigger updates, or access window-specific
 * functionality like drag operations.
 *
 * @throws IllegalStateException if accessed outside of a ComposeServiceFloatingWindow context
 */
val LocalServiceFloatingWindow: ProvidableCompositionLocal<ComposeServiceFloatingWindow> =
    compositionLocalOf {
        noLocalProvidedFor("LocalServiceFloatingWindow")
    }

private fun noLocalProvidedFor(name: String): Nothing {
    error("CompositionLocal $name not present")
}
