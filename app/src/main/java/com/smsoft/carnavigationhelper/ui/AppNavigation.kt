package com.smsoft.carnavigationhelper.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.smsoft.carnavigationhelper.data.Main
import com.smsoft.carnavigationhelper.data.Player
import com.smsoft.carnavigationhelper.data.Screen
import com.smsoft.carnavigationhelper.ui.screen.main.MainScreen
import com.smsoft.carnavigationhelper.ui.screen.player.PlayerScreen
import com.smsoft.carnavigationhelper.ui.screen.player_settings.PlayerSettingsScreen
import com.smsoft.carnavigationhelper.ui.screen.settings.SettingsScreen
import com.smsoft.carnavigationhelper.ui.theme.CarNavigationHelperTheme
import dagger.hilt.android.AndroidEntryPoint

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Main()) {
        composable<Main> { backStackEntry ->
            val args = backStackEntry.toRoute<Main>()
            MainScreen(
                isForceNavigation = args.isForceNavigation,
                onPlayAction = {
                    navController.navigate(Player(isForceNavigation = true))
                },
                onPlayerAction = {
                    navController.navigate(Player(isForceNavigation = false))
                },
                onSettingsAction = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }

        composable<Player> { backStackEntry ->
            val args = backStackEntry.toRoute<Main>()
            PlayerScreen(
                onBack = {
                    navController.navigate(Main(isForceNavigation = true))
                },
                onSettingsAction = {
                    navController.navigate(Screen.PlayerSettings.route)
                },
                onPlay = {
                    if (args.isForceNavigation) {
                        navController.navigate(Main(isForceNavigation = true))
                    }
                },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen {
                navController.navigateUp()
            }
        }

        composable(Screen.PlayerSettings.route) {
            PlayerSettingsScreen {
                navController.navigateUp()
            }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarNavigationHelperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppNavigation()
                }
           }
        }
    }
}