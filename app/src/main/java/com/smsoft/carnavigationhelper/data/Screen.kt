package com.smsoft.carnavigationhelper.data

import kotlinx.serialization.Serializable

sealed class Screen(val route: String) {
    data object Settings : Screen("settings")
}

@Serializable
data class Main(val isForceNavigation: Boolean = false)

@Serializable
data class Player(val isForceNavigation: Boolean = false)