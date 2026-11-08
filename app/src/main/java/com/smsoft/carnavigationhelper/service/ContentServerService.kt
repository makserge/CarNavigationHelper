package com.smsoft.carnavigationhelper.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.data.server.ContentServer
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServerEntryPoint {
    fun userPreferences(): UserPreferencesRepository
}

// Runs the second sync server in the foreground, so Android keeps it alive while the car app is in the background.
// Reads its settings on each start; the settings screen restarts it after a change.
class ContentServerService : Service() {
    private var server: ContentServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = EntryPointAccessors
                .fromApplication(applicationContext, ServerEntryPoint::class.java)
                .userPreferences()
            val enabled = prefs.serverEnabledFlow.first()
            val folder = prefs.playerPlaylistPathFlow.first()
            val port = prefs.serverPortFlow.first()
            val key = prefs.serverKeyFlow.first()
            server?.stop()
            server = null
            if (!enabled) {
                stopSelf()
                return@launch
            }
            if (folder.isBlank()) {
                toast(getString(R.string.server_error_folder))
                stopSelf()
                return@launch
            }
            if (key.length < MIN_KEY_LENGTH) {
                toast(getString(R.string.server_error_key))
                stopSelf()
                return@launch
            }
            try {
                server = ContentServer(applicationContext, folder.toUri(), port, key).also { it.start() }
                toast(getString(R.string.server_started, port))
            } catch (e: Exception) {
                toast(getString(R.string.server_error_start, e.message ?: e.javaClass.simpleName))
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    private fun toast(text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show() }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.server_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.server_notification_title))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL_ID = "second_sync_server"
        private const val NOTIFICATION_ID = 4731
        const val MIN_KEY_LENGTH = 16

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ContentServerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ContentServerService::class.java))
        }
    }
}
