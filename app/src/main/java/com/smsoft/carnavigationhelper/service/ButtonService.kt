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
            context.startService(intent)
        }

        fun showButton(context: Context) {
            val intent = Intent(context, ButtonService::class.java).apply {
                putExtra(SHOW_BUTTON, true)
            }
            context.startService(intent)
        }

        fun hideButton(context: Context) {
            val intent = Intent(context, ButtonService::class.java).apply {
                putExtra(SHOW_BUTTON, false)
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var serviceOverlay: ServiceOverlay

    override fun onCreate() {
        super.onCreate()
        _serviceStarted.update { true }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val context = this
        serviceScope.launch {
            serviceOverlay.navigateToNext.collect { shouldNavigate ->
                if (shouldNavigate) {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }
        }
        if (intent != null) {
            val isShowButton = intent.getBooleanExtra(SHOW_BUTTON, false)
            if (isShowButton) {
                serviceOverlay.show()
            } else {
                serviceOverlay.hide()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        _serviceStarted.update { false }
        // Call close for cleanup and it will hide it in the process
        serviceOverlay.close()
        super.onDestroy()
    }
}