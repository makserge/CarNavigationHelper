package com.github.only52607.compose.service

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.github.only52607.compose.core.dragCoreFloatingWindow

/**
 * Adds drag functionality to make a floating window draggable.
 *
 * This modifier enables the user to drag the floating window around the screen by
 * applying touch gestures to the composable it's attached to. The window position
 * is automatically constrained to stay within screen bounds.
 *
 * Example usage:
 * ```kotlin
 * FloatingActionButton(
 *     modifier = Modifier.dragServiceFloatingWindow(),
 *     onClick = { /* handle click */ }
 * ) {
 *     Icon(Icons.Filled.Call, "Call")
 * }
 * ```
 *
 * @param onDragStart Callback invoked when drag gesture starts. Receives the initial touch offset.
 * @param onDragEnd Callback invoked when drag gesture ends normally.
 * @param onDragCancel Callback invoked when drag gesture is cancelled.
 * @param onDrag Optional callback invoked during drag with the current window coordinates (left, top).
 * @return A [Modifier] that enables drag functionality for the floating window.
 */
@Composable
fun Modifier.dragServiceFloatingWindow(
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onDrag: ((Int, Int) -> Unit)? = null,
): Modifier {
    val floatingWindow = LocalServiceFloatingWindow.current

    return this.dragCoreFloatingWindow(
        floatingWindow = floatingWindow,
        onDragStart = onDragStart,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
        onDrag = onDrag,
    )
}
