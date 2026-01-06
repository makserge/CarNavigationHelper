package com.github.only52607.compose.window

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import com.github.only52607.compose.core.rememberCoreFloatingWindowInteractionSource

@Composable
fun rememberFloatingWindowInteractionSource(): MutableInteractionSource {
    val floatingWindow = LocalFloatingWindow.current
    val interactionSource = rememberCoreFloatingWindowInteractionSource(floatingWindow)

    return interactionSource
}
