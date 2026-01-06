package com.github.only52607.compose.core

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

@Composable
internal fun Modifier.dragCoreFloatingWindow(
    floatingWindow: CoreFloatingWindow,
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onDrag: ((Int, Int) -> Unit)? = null,
) = this.pointerInput(floatingWindow) {
    detectDragGestures(
        onDragStart = onDragStart,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
    ) { change, dragAmount ->
        change.consume()

        val targetX = floatingWindow.windowParams.x + dragAmount.x.roundToInt()
        val targetY = floatingWindow.windowParams.y + dragAmount.y.roundToInt()

        val left = targetX.coerceIn(0, floatingWindow.maxXCoordinate)
        val top = targetY.coerceIn(0, floatingWindow.maxYCoordinate)

        floatingWindow.updateCoordinate(left, top)

        onDrag?.invoke(left, top)
    }
}
