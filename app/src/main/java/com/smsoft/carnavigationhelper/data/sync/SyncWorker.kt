package com.smsoft.carnavigationhelper.data.sync

import android.content.Context
import com.smsoft.carnavigationhelper.data.wifi.WifiNetworks
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException

// Hilt entry point, so the worker can use the singleton repository without a Hilt worker factory
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun contentSyncRepository(): ContentSyncRepository
}

// Automatic sync, scheduled by SyncScheduler
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): ListenableWorker.Result {
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, SyncEntryPoint::class.java)
            .contentSyncRepository()
        // Switched off after the job was queued
        if (!repository.autoSyncEnabled()) return ListenableWorker.Result.success()
        // Only on the chosen Wi-Fi network (empty = any unmetered network)
        val wanted = repository.selectedWifi()
        if (wanted.isNotBlank() && WifiNetworks.currentSsid(applicationContext) != wanted) {
            return ListenableWorker.Result.success()
        }
        return try {
            repository.syncNow(applicationContext)
            ListenableWorker.Result.success()
        } catch (e: SyncException) {
            if (e.retryable) ListenableWorker.Result.retry() else ListenableWorker.Result.failure()
        } catch (_: IOException) {
            ListenableWorker.Result.retry()
        }
    }
}
