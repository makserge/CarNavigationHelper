package com.smsoft.carnavigationhelper.service

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.only52607.compose.service.ComposeServiceFloatingWindow
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository
import com.smsoft.carnavigationhelper.ui.floating.FloatingViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import com.smsoft.carnavigationhelper.ui.floating.FloatingScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@ServiceScoped
class ServiceOverlay @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    @param:ApplicationContext private val context: Context,
) {

    private var viewModel: FloatingViewModel? = null
    private var floatingWindow: ComposeServiceFloatingWindow? = null
    lateinit var navigateToNext: SharedFlow<Boolean>

    init {
        floatingWindow = createFloatingWindow()
    }

    private fun createFloatingWindow(): ComposeServiceFloatingWindow {
        viewModel = FloatingViewModel(userPreferencesRepository)

        navigateToNext = viewModel!!.navigateToNext

        return ComposeServiceFloatingWindow(context).apply {
            setContent {
                var initialization by remember { mutableStateOf(false) }
                viewModel?.let { vm ->
                    LaunchedEffect(Unit) {
                        vm.location.first().let { location ->
                            windowParams.x = location.first
                            windowParams.y = location.second
                            update()
                        }
                        initialization = true
                    }
                    if (initialization) {
                        FloatingScreen(vm)
                    }
                }
            }
        }
    }

    fun show() {
        if (floatingWindow == null) {
            floatingWindow = createFloatingWindow()
        }
        floatingWindow?.let { window ->
            if (!window.isShowing.value) {
                window.show()
            }
        }
    }

    fun hide() {
        floatingWindow?.let { window ->
            if (window.isShowing.value) {
                window.hide()
            }
        }
    }

    fun close() {
        floatingWindow?.let { window ->
            window.hide()
            window.close()
        }
        floatingWindow = null
        viewModel = null
    }
}
