package com.github.only52607.compose.core

import android.content.Context
import android.util.Log
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.launch

@Composable
internal fun rememberCoreFloatingWindowInteractionSource(
    floatingWindow: CoreFloatingWindow,
): MutableInteractionSource {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    var focusIndication: FocusInteraction.Focus? by remember {
        mutableStateOf(null)
    }
    val isFocused by remember {
        derivedStateOf {
            focusIndication != null
        }
    }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            when (it) {
                is FocusInteraction.Focus -> {
                    focusIndication = it
                    Log.d(TAG, "FocusInteraction.Focus: $it")
                    // Remove FLAG_NOT_FOCUSABLE to allow keyboard to appear
                    if (floatingWindow.windowParams.flags and FLAG_NOT_FOCUSABLE != 0) {
                        Log.d(TAG, "Removing FLAG_NOT_FOCUSABLE to summon keyboard")
                        floatingWindow.windowParams.flags =
                            floatingWindow.windowParams.flags and FLAG_NOT_FOCUSABLE.inv()
                        floatingWindow.update()

                        // Explicitly request to show the soft keyboard
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(floatingWindow.decorView, InputMethodManager.SHOW_IMPLICIT)
                    }
                }

                is FocusInteraction.Unfocus -> {
                    focusIndication = null
                    Log.d(TAG, "FocusInteraction.Unfocus: $it")

                    if ((floatingWindow.windowParams.flags and FLAG_NOT_FOCUSABLE) == 0) {
                        Log.d(TAG, "Restoring FLAG_NOT_FOCUSABLE")
                        floatingWindow.windowParams.flags =
                            floatingWindow.windowParams.flags or FLAG_NOT_FOCUSABLE
                        floatingWindow.update()
                    }
                }
            }
        }
    }

    val scope = rememberCoroutineScope()

    DisposableEffect(floatingWindow.decorView) {
        ViewCompat.setOnApplyWindowInsetsListener(floatingWindow.decorView) { v, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (!imeVisible && isFocused && floatingWindow.windowParams.flags and FLAG_NOT_FOCUSABLE == 0) {
                Log.d(TAG, "IME closed, restoring FLAG_NOT_FOCUSABLE")
                floatingWindow.windowParams.flags =
                    floatingWindow.windowParams.flags or FLAG_NOT_FOCUSABLE
                floatingWindow.update()
                focusIndication?.let {
                    Log.d(TAG, "Unfocusing window: $it")
                    scope.launch {
                        focusManager.clearFocus()
                        focusIndication = null
                    }
                }
            }
            insets
        }
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(floatingWindow.decorView, null)
        }
    }

    return interactionSource
}

private const val TAG = "CoreFloatingWindow"
