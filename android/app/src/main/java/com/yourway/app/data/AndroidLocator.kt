package com.yourway.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class AndroidLocator(private val context: Context) : Locator {

    @SuppressLint("MissingPermission")
    override suspend fun current(): LatLon? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = manager.getProviders(true)
        val last = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        val fresh = last?.takeIf { System.currentTimeMillis() - it.time < RECENT_MILLIS }
        val fix = fresh ?: requestFix(manager, providers) ?: last
        return fix?.let { LatLon(it.latitude, it.longitude) }
    }

    private fun hasPermission() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    private suspend fun requestFix(manager: LocationManager, providers: List<String>): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER } ?: providers.firstOrNull() ?: return null
        return withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { continuation.resume(it) }
            }
        }
    }

    private companion object {
        const val RECENT_MILLIS = 2 * 60 * 1000L
        const val TIMEOUT_MILLIS = 8_000L
    }
}
