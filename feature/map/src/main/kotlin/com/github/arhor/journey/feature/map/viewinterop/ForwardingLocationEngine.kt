package com.github.arhor.journey.feature.map.viewinterop

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
import java.util.concurrent.ConcurrentHashMap

internal class ForwardingLocationEngine(context: Context) : LocationEngine {

    private val appContext = context.applicationContext
    private val locationManager = requireNotNull(appContext.getSystemService(LocationManager::class.java))
    private val listeners = ConcurrentHashMap<LocationEngineCallback<LocationEngineResult>, LocationListener>()

    @SuppressLint("MissingPermission")
    override fun getLastLocation(callback: LocationEngineCallback<LocationEngineResult>) {
        requireLocationPermission()

        val location = accessibleEnabledProviders()
            .mapNotNull(::getLastKnownLocation)
            .maxWithOrNull(compareBy<Location> { it.elapsedRealtimeNanos }.thenBy { it.time })

        if (location != null) {
            callback.onSuccess(LocationEngineResult.create(location))
        } else {
            callback.onFailure(Exception("Last location unavailable"))
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        callback: LocationEngineCallback<LocationEngineResult>,
        looper: Looper?,
    ) {
        requireLocationPermission()
        removeLocationUpdates(callback)

        val listener = ForwardingLocationListener(callback)
        val callbackLooper = looper ?: Looper.getMainLooper()
        var hasRegisteredProvider = false
        var securityException: SecurityException? = null

        listeners[callback] = listener

        for (provider in requestProviders(request)) {
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    request.interval,
                    request.displacement,
                    listener,
                    callbackLooper,
                )
                hasRegisteredProvider = true
            } catch (_: IllegalArgumentException) {
                // Provider disappeared between discovery and registration.
            } catch (exception: SecurityException) {
                securityException = exception
            }
        }

        if (!hasRegisteredProvider) {
            listeners.remove(callback)
            securityException?.let { throw it }
            callback.onFailure(Exception("No enabled location providers available"))
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        pendingIntent: PendingIntent,
    ) {
        requireLocationPermission()

        var hasRegisteredProvider = false
        var securityException: SecurityException? = null

        for (provider in requestProviders(request)) {
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    request.interval,
                    request.displacement,
                    pendingIntent,
                )
                hasRegisteredProvider = true
            } catch (_: IllegalArgumentException) {
                // Provider disappeared between discovery and registration.
            } catch (exception: SecurityException) {
                securityException = exception
            }
        }

        if (!hasRegisteredProvider) {
            securityException?.let { throw it }
        }
    }

    override fun removeLocationUpdates(callback: LocationEngineCallback<LocationEngineResult>) {
        listeners.remove(callback)?.let(locationManager::removeUpdates)
    }

    override fun removeLocationUpdates(pendingIntent: PendingIntent?) {
        if (pendingIntent != null) {
            locationManager.removeUpdates(pendingIntent)
        }
    }

    private fun requestProviders(request: LocationEngineRequest): List<String> {
        val enabledProviders = accessibleEnabledProviders()

        return when (request.priority) {
            LocationEngineRequest.PRIORITY_NO_POWER -> {
                enabledProviders.filter { it == LocationManager.PASSIVE_PROVIDER }
            }

            LocationEngineRequest.PRIORITY_LOW_POWER,
            LocationEngineRequest.PRIORITY_BALANCED_POWER_ACCURACY,
            -> {
                enabledProviders.filter { it != LocationManager.GPS_PROVIDER }
            }

            LocationEngineRequest.PRIORITY_HIGH_ACCURACY -> enabledProviders
            else -> enabledProviders
        }
    }

    private fun accessibleEnabledProviders(): List<String> {
        val hasFinePermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

        return locationManager
            .getProviders(true)
            .orEmpty()
            .filter { provider ->
                hasFinePermission || provider != LocationManager.GPS_PROVIDER
            }
            .distinct()
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(provider: String): Location? {
        return try {
            locationManager.getLastKnownLocation(provider)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun requireLocationPermission() {
        if (
            !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            throw SecurityException("Location permission not granted")
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private class ForwardingLocationListener(
        private val callback: LocationEngineCallback<LocationEngineResult>,
    ) : LocationListener {

        override fun onLocationChanged(location: Location) {
            callback.onSuccess(LocationEngineResult.create(location))
        }
    }
}
