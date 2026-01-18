package com.smsoft.carnavigationhelper.ui.composable

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.data.LocationType
import com.smsoft.carnavigationhelper.ui.screen.main.MainViewModel

@Composable
fun ActionButtons(
    modifier: Modifier,
    isEnabled: Boolean,
    locationType: LocationType,
    countdownTimer: Int,
    viewModel: MainViewModel
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (locationType != LocationType.UNKNOWN) {
            Text(
                stringResource(
                    R.string.countdown_timer_text,
                    stringResource(locationType.resId),
                    countdownTimer
                )
            )
        }
        if (locationType == LocationType.WORK) {
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = {
                    viewModel.openNavAppLocationWork(context)
                },
                enabled = isEnabled
            ) {
                Text(stringResource(R.string.go_to_work_button))
            }
        } else {
            ElevatedButton(
                modifier = Modifier.padding(top = 16.dp),
                onClick = {
                    viewModel.openNavAppLocationWork(context)
                },
                enabled = isEnabled
            ) {
                Text(stringResource(R.string.go_to_work_button))
            }
        }
        if (locationType == LocationType.HOME) {
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = {
                    viewModel.openNavAppLocationHome(context)
                },
                enabled = isEnabled
            ) {
                Text(stringResource(R.string.go_to_home_button))
            }
        } else {
            ElevatedButton(
                modifier = Modifier.padding(top = 16.dp),
                onClick = {
                    viewModel.openNavAppLocationHome(context)
                },
                enabled = isEnabled
            ) {
                Text(stringResource(R.string.go_to_home_button))
            }
        }
        ElevatedButton(
            modifier = Modifier.padding(top = 96.dp),
            onClick = {
                viewModel.openNavApp(context)
            },
            enabled = isEnabled
        ) {
            Text(stringResource(R.string.open_nav_button))
        }
        ElevatedButton(
            modifier = Modifier.padding(top = 16.dp),
            onClick = {
                viewModel.closeApp(activity)
            },
            enabled = isEnabled
        ) {
            Text(stringResource(R.string.close_app_button))
        }
    }
}