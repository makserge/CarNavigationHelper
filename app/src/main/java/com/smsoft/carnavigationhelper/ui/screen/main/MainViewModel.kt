package com.smsoft.carnavigationhelper.ui.screen.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
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

    // Set while this screen waits for its location. A late location result must not start
    // the countdown after the user has left the screen or chosen another action.
    private var isCountdownArmed = false

    val waitingForInternet = waitingForInternetPrivate.asStateFlow()

    private fun openNavAppLocation(
        context: Context,
        location: GeoPoint
    ) {
        cancelCountDownTimer()
        ButtonService.showButton(context)
        launchNavigationApp(location)
    }

    private fun launchNavigationApp(location: GeoPoint) {
        cancelNavigation()
        navigationJob = coroutineScope.launch {
            var intent: Intent
            val type = navType.first()
            if (type == NavType.IGO.name) {
                val navUri = "geo:" + location.latitude + "," + location.longitude + "?q=" + location.latitude + "," + location.longitude
                intent = Intent(Intent.ACTION_VIEW, navUri.toUri()).apply {
                    `package` = IGO_PACKAGE_NAME
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                awaitStableInternet()
                val navUri = "waze://?ll=" + location.latitude + "," + location.longitude + "&navigate=yes"
                intent = Intent(Intent.ACTION_VIEW, navUri.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            startIntent(intent)
        }
    }

    private fun cancelNavigation() {
        navigationJob?.cancel()
        navigationJob = null
    }

    fun openNavApp(context: Context) {
        cancelNavigation()
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
                    startIntent(intent)
                }
            }
        }
    }

    /**
     * Waze calculates the route on its servers. On weak mobile reception Android still reports the
     * network as validated while real requests stall, and Waze started then can't follow the
     * destination. A good connection answers the first probe quickly and Waze starts right away;
     * otherwise waits (without time limit) until several probes in a row succeed.
     */
    private suspend fun awaitStableInternet() {
        if (isInternetReachable(FAST_INTERNET_PROBE_TIMEOUT_MS)) return

        waitingForInternetPrivate.update { true }
        try {
            var successes = 0
            while (successes < STABLE_INTERNET_PROBES) {
                delay(INTERNET_PROBE_INTERVAL_MS)
                successes = if (isInternetReachable()) successes + 1 else 0
            }
        } finally {
            waitingForInternetPrivate.update { false }
        }
    }

    private suspend fun isInternetReachable(timeoutMs: Long = INTERNET_PROBE_TIMEOUT_MS): Boolean {
        // A hung DNS lookup ignores socket timeouts, so the deadline is enforced from outside
        val probe = coroutineScope.async(Dispatchers.IO) { runInternetProbe() }
        return withTimeoutOrNull(timeoutMs) { probe.await() }
            ?: false.also { probe.cancel() }
    }

    private fun runInternetProbe(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        var connection: HttpURLConnection? = null
        return try {
            connection = (network.openConnection(URL(INTERNET_PROBE_URL)) as HttpURLConnection).apply {
                connectTimeout = INTERNET_PROBE_TIMEOUT_MS.toInt()
                readTimeout = INTERNET_PROBE_TIMEOUT_MS.toInt()
                instanceFollowRedirects = false
                useCaches = false
            }
            connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun startIntent(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.nav_app_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    fun closeApp(activity: Activity?) {
        cancelCountDownTimer()
        cancelNavigation()
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
        // A destination is already chosen and waits for the internet - don't start the countdown again
        if (navigationJob?.isActive == true) return
        isCountdownArmed = true

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(LOCATION_REQUEST_DURATION_MS)
            .build()

        // Without mobile data there's no assisted GPS, so a fresh fix may not arrive in time.
        // The car hasn't moved since it was parked, so the last known location is good enough then.
        fusedLocationClient.getCurrentLocation(request, null).addOnCompleteListener { task ->
            val current = if (task.isSuccessful) task.result else null
            if (current != null) {
                onLocationDetermined(current)
            } else {
                fusedLocationClient.lastLocation.addOnSuccessListener { last ->
                    last?.let { onLocationDetermined(it) }
                }
            }
        }
    }

    private fun onLocationDetermined(location: Location) {
        coroutineScope.launch {
            val home = homePosition.first()
            val work = workPosition.first()
            val type = when {
                location.isNear(home) -> LocationType.WORK
                location.isNear(work) -> LocationType.HOME
                else -> LocationType.UNKNOWN
            }
            locationTypePrivate.update { type }
            if (type != LocationType.UNKNOWN) {
                restartCountDownTimer()
            }
        }
    }

    private fun Location.isNear(point: GeoPoint): Boolean =
        latitude in (point.latitude - LOCATION_RADIUS)..(point.latitude + LOCATION_RADIUS)
                && longitude in (point.longitude - LOCATION_RADIUS)..(point.longitude + LOCATION_RADIUS)

    fun launchPlayer(onPlay: () -> Unit) {
        // Navigate after the resume callback has finished, not while it runs
        coroutineScope.launch(Dispatchers.Main) {
            onPlay()
        }
    }

    private fun restartCountDownTimer() {
        coroutineScope.launch {
            val timerDelay = countdownTimerDelay.first()
            if (!isCountdownArmed) return@launch

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
        isCountdownArmed = false
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

    override fun onCleared() {
        // The countdown belongs to this screen, it must not fire after the screen is gone
        isCountdownArmed = false
        countDownTimer?.cancel()
    }
}

// Shared between MainViewModel instances: each Main screen entry gets its own ViewModel,
// but a navigation that waits for the internet must survive them.
private var navigationJob: Job? = null
private val waitingForInternetPrivate = MutableStateFlow(false)

const val IGO_PACKAGE_NAME = "iGO.Israel"
const val LOCATION_RADIUS = 0.01
const val LOCATION_REQUEST_DURATION_MS = 20_000L
const val INTERNET_PROBE_URL = "https://connectivitycheck.gstatic.com/generate_204"
const val INTERNET_PROBE_TIMEOUT_MS = 5_000L
const val FAST_INTERNET_PROBE_TIMEOUT_MS = 2_000L
const val INTERNET_PROBE_INTERVAL_MS = 2_000L
const val STABLE_INTERNET_PROBES = 3
