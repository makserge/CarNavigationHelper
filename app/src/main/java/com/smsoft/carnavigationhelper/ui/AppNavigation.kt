package com.smsoft.carnavigationhelper.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hasRoute
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
                    // Main(false) only starts the player, it must not stay in the back stack
                    navController.navigate(Player(isForceNavigation = true)) {
                        popUpTo<Main> { inclusive = true }
                    }
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
            val args = backStackEntry.toRoute<Player>()
            // Player(true) is the only back stack entry until the music starts, then Main(true)
            // replaces it. A second call (Back plus playback start) must not add another Main.
            val openForcedMain: () -> Unit = {
                if (navController.currentDestination?.hasRoute<Main>() != true) {
                    navController.navigate(Main(isForceNavigation = true)) {
                        popUpTo<Player> { inclusive = true }
                    }
                }
            }
            val onBack: () -> Unit = {
                if (args.isForceNavigation) openForcedMain() else navController.navigateUp()
            }
            // There is no Main below Player(true) to go back to
            BackHandler(enabled = args.isForceNavigation, onBack = onBack)
            PlayerScreen(
                onBack = onBack,
                onSettingsAction = {
                    navController.navigate(Screen.PlayerSettings.route)
                },
                onPlay = {
                    if (args.isForceNavigation) {
                        openForcedMain()
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
        installSplashScreen()
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

    // Light/dark switches are handled without recreating the activity (configChanges="uiMode"),
    // so a running countdown or startup isn't interrupted. Compose follows by itself, the system bars need this.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
    }
}