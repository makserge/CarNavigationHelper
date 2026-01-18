package com.smsoft.carnavigationhelper.ui.screen.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.CountDownTimer
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.data.GeoPoint
import com.smsoft.carnavigationhelper.data.LocationType
import com.smsoft.carnavigationhelper.data.NavType
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_COUNTDOWN_TIMER_DELAY
import com.smsoft.carnavigationhelper.service.ButtonService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val coroutineScope: CoroutineScope
) : ViewModel() {

    private val homePosition: Flow<GeoPoint>
        get() = userPreferencesRepository.homePositionFlow

    private val workPosition: Flow<GeoPoint>
        get() = userPreferencesRepository.workPositionFlow

    private val navType: Flow<String>
        get() = userPreferencesRepository.navTypeFlow

    private val locationTypePrivate = MutableStateFlow(LocationType.UNKNOWN)
    val locationType = locationTypePrivate.asStateFlow()

    private val countdownTimerDelay: Flow<Int>
        get() = userPreferencesRepository.countdownTimerDelayFlow

    private val countdownTimerPrivate = MutableStateFlow(DEFAULT_COUNTDOWN_TIMER_DELAY)
    val countdownTimer = countdownTimerPrivate.asStateFlow()

    private var countDownTimer: CountDownTimer? = null

    private fun openNavAppLocation(context: Context, location: GeoPoint) {
        ButtonService.showButton(context)
        cancelCountDownTimer()
        launchPlayer()

        coroutineScope.launch {
            delay(NAV_START_DELAY)

            val type = navType.first()
            var intent: Intent
            if (type == NavType.IGO.name) {
                val navUri = "geo:" + location.latitude + "," + location.longitude + "?q=" + location.latitude + "," + location.longitude
                intent = Intent(Intent.ACTION_VIEW, navUri.toUri()).apply {
                    `package` = IGO_PACKAGE_NAME
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                val navUri = "waze://?ll=" + location.latitude + "," + location.longitude + "&navigate=yes"
                intent = Intent(Intent.ACTION_VIEW, navUri.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            startIntent(intent)
        }
    }

    fun openNavApp(context: Context) {
        ButtonService.showButton(context)
        cancelCountDownTimer()
        coroutineScope.launch {
            val type = navType.first()
            var intent: Intent
            if (type == NavType.IGO.name) {
                intent = Intent(Intent.ACTION_MAIN).apply {
                    `package` = IGO_PACKAGE_NAME
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startIntent(intent)
            } else {
                try {
                    intent = Intent(Intent.ACTION_VIEW, "waze://".toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    intent =
                        Intent(Intent.ACTION_VIEW, "market://details?id=com.waze".toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    context.startActivity(intent)
                }
            }
        }
    }

    private fun startIntent(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast(context).apply {
                setText(R.string.nav_app_not_found)
                show()
            }
        }
    }

    fun closeApp(activity: Activity?) {
        cancelCountDownTimer()
        activity?.let {
            it.finish()
            ButtonService.showButton(it.applicationContext)
        }
    }

    fun checkLocationPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startNavigationForLocation(
        fusedLocationClient: FusedLocationProviderClient
    ) {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fusedLocationClient.getCurrentLocation(request, null).addOnSuccessListener { location ->
            location?.let {
                coroutineScope.launch {
                    val home = homePosition.first()
                    if ((it.latitude in (home.latitude - LOCATION_RADIUS)..(home.latitude + LOCATION_RADIUS))
                        && (it.longitude in (home.longitude - LOCATION_RADIUS)..(home.longitude + LOCATION_RADIUS))) {
                        locationTypePrivate.update { LocationType.WORK }
                        restartCountDownTimer()
                    } else {
                        coroutineScope.launch {
                            val work = workPosition.first()
                            if ((it.latitude in (work.latitude - LOCATION_RADIUS)..(work.latitude + LOCATION_RADIUS))
                                && (it.longitude in (work.longitude - LOCATION_RADIUS)..(work.longitude + LOCATION_RADIUS))) {
                                locationTypePrivate.update { LocationType.HOME }
                                restartCountDownTimer()
                            } else {
                                locationTypePrivate.update { LocationType.UNKNOWN }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchPlayer() {
        val intent = Intent().apply {
            component = ComponentName(AIMP_PACKAGE_NAME, AIMP_ACTIVITY_NAME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast(context).apply {
                setText(R.string.player_app_not_found)
                show()
            }
        }
    }

    private fun restartCountDownTimer() {
        coroutineScope.launch {
            val timerDelay = countdownTimerDelay.first()

            countdownTimerPrivate.update { timerDelay }
            countDownTimer?.cancel()

            countDownTimer = object : CountDownTimer(timerDelay * 1000L, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    countdownTimerPrivate.update { (millisUntilFinished / 1000).toInt() }
                }

                override fun onFinish() {
                    when (locationType.value) {
                        LocationType.WORK -> openNavAppLocationWork(context)
                        LocationType.HOME -> openNavAppLocationHome(context)
                        else -> {}
                    }
                }
            }.start()
        }
    }

    fun cancelCountDownTimer() {
        countDownTimer?.cancel()
        coroutineScope.launch {
            val timerDelay = countdownTimerDelay.first()
            countdownTimerPrivate.update { timerDelay }
        }
    }

    fun openNavAppLocationWork(context: Context) {
        coroutineScope.launch {
            openNavAppLocation(context, workPosition.first())
        }
    }

    fun openNavAppLocationHome(context: Context) {
        coroutineScope.launch {
            openNavAppLocation(context, homePosition.first())
        }
    }
}

const val IGO_PACKAGE_NAME = "iGO.Israel"
const val AIMP_PACKAGE_NAME = "com.aimp.player"
const val AIMP_ACTIVITY_NAME = "com.aimp.player.ui.activities.main.MainActivity"
const val LOCATION_RADIUS = 0.01
const val NAV_START_DELAY = 10000L // 5s
