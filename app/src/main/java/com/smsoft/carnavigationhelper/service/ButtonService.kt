package com.smsoft.carnavigationhelper.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.smsoft.carnavigationhelper.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ButtonService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val SHOW_BUTTON = "SHOW_BUTTON"

        private var _serviceStarted = MutableStateFlow(false)
        val serviceStarted: StateFlow<Boolean>
            get() = _serviceStarted.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, ButtonService::class.java)
            startService(context, intent)
        }

        fun showButton(context: Context) {
            val intent = Intent(context, ButtonService::class.java).apply {
                putExtra(SHOW_BUTTON, true)
            }
            startService(context, intent)
        }

        fun hideButton(context: Context) {
            val intent = Intent(context, ButtonService::class.java).apply {
                putExtra(SHOW_BUTTON, false)
            }
            startService(context, intent)
        }

        // Not allowed from the background after the system stopped the service of an idle app,
        // the button then just stays hidden instead of crashing the app
        private fun startService(context: Context, intent: Intent) {
            try {
                context.startService(intent)
            } catch (_: IllegalStateException) {
            }
        }
    }

    @Inject
    lateinit var serviceOverlay: ServiceOverlay

    override fun onCreate() {
        super.onCreate()
        _serviceStarted.update { true }
        val context = this
        // Collect once per service instance, onStartCommand runs on every start/show/hide
        serviceScope.launch {
            serviceOverlay.navigateToNext.collect { shouldNavigate ->
                if (shouldNavigate) {
                    // Same action/category as the launcher intent, so the existing task is brought to the front
                    val intent = Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent is a sticky restart after process death, so bring the button back
        val isShowButton = intent?.getBooleanExtra(SHOW_BUTTON, false) ?: true
        if (isShowButton) {
            serviceOverlay.show()
        } else {
            serviceOverlay.hide()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        _serviceStarted.update { false }
        serviceOverlay.close()
        super.onDestroy()
    }
}