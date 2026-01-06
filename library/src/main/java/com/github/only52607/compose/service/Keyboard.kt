package com.github.only52607.compose.service

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import com.github.only52607.compose.core.rememberCoreFloatingWindowInteractionSource

@Composable
fun rememberServiceFloatingWindowInteractionSource(): MutableInteractionSource {
    val floatingWindow = LocalServiceFloatingWindow.current
    val interactionSource = rememberCoreFloatingWindowInteractionSource(floatingWindow)

    return interactionSource
}
