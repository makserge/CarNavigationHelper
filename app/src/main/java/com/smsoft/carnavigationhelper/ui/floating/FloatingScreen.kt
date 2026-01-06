package com.smsoft.carnavigationhelper.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.only52607.compose.service.dragServiceFloatingWindow
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.ui.theme.CarNavigationHelperTheme
import kotlinx.coroutines.launch

@Composable
fun FloatingScreen(
    vm: FloatingViewModel = viewModel(),
) {
    val scope = rememberCoroutineScope()
    CarNavigationHelperTheme {
        FloatingActionButton(
            modifier = Modifier
                .dragServiceFloatingWindow(
                    onDrag = { x, y ->
                        vm.updateLocation(x, y)
                    },
                ),
            onClick = {
                scope.launch {
                    vm.showApp()
                }
            },
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = stringResource(R.string.open_home),
                tint = Color(0xFF30D5C8),
                modifier = Modifier
                    .background(Color.LightGray)
                    .padding(16.dp)
            )
        }
    }
}
