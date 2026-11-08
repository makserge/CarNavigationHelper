package com.smsoft.carnavigationhelper.data.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat

// Wi-Fi names (SSIDs). Android only shows names when location permission is granted and location is on.
object WifiNetworks {

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // Name of the Wi-Fi network the phone is connected to, or null (not on Wi-Fi, or unknown)
    @Suppress("DEPRECATION")
    fun currentSsid(context: Context): String? {
        if (!hasLocationPermission(context)) return null
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        val ssid = runCatching { wifi.connectionInfo?.ssid }.getOrNull()?.removeSurrounding("\"") ?: return null
        return if (ssid.isBlank() || ssid == "<unknown ssid>") null else ssid
    }

    // Names of the Wi-Fi networks in range (from the last scan), sorted and without duplicates
    @Suppress("DEPRECATION")
    fun visibleNetworks(context: Context): List<String> {
        if (!hasLocationPermission(context)) return emptyList()
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return emptyList()
        val results = runCatching { wifi.scanResults }.getOrNull() ?: return emptyList()
        return results.map { it.SSID.orEmpty() }.filter { it.isNotBlank() }.distinct().sorted()
    }
}
