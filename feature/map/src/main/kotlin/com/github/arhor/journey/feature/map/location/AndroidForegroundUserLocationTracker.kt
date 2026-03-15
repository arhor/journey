package com.github.arhor.journey.feature.map.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.getSystemService
import com.github.arhor.journey.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class AndroidForegroundUserLocationTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) : ForegroundUserLocationTracker {

    @SuppressLint("MissingPermission")
    override fun observeLocations(): Flow<UserLocationUpdate> = callbackFlow {
        val locationManager = context.getSystemService<LocationManager>() ?: run {
            trySend(UserLocationUpdate.TemporarilyUnavailable)
            close()
            return@callbackFlow
        }

        if (!context.hasLocationPermission()) {
            trySend(UserLocationUpdate.PermissionDenied)
            close()
            return@callbackFlow
        }

        if (!locationManager.isLocationEnabled) {
            trySend(UserLocationUpdate.LocationServicesDisabled)
            close()
            return@callbackFlow
        }

        val providers = listOfNotNull(
            LocationManager.GPS_PROVIDER.takeIf { locationManager.isProviderEnabled(it) },
            LocationManager.NETWORK_PROVIDER.takeIf { locationManager.isProviderEnabled(it) },
        )

        if (providers.isEmpty()) {
            trySend(UserLocationUpdate.LocationServicesDisabled)
            close()
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                trySend(UserLocationUpdate.Available(location.toGeoPoint()))
            }

            override fun onProviderDisabled(provider: String) {
                val hasAnyEnabledProvider = providers.any { locationManager.isProviderEnabled(it) }
                if (!hasAnyEnabledProvider) {
                    trySend(UserLocationUpdate.LocationServicesDisabled)
                }
            }

            override fun onProviderEnabled(provider: String) {
                trySend(UserLocationUpdate.TemporarilyUnavailable)
            }
        }

        providers
            .asSequence()
            .mapNotNull(locationManager::getLastKnownLocation)
            .firstOrNull()
            ?.let {
                trySend(UserLocationUpdate.Available(it.toGeoPoint()))
            } ?: trySend(UserLocationUpdate.TemporarilyUnavailable)

        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                LOCATION_UPDATE_INTERVAL_MS,
                LOCATION_UPDATE_MIN_DISTANCE_METERS,
                listener,
                Looper.getMainLooper(),
            )
        }

        awaitClose {
            locationManager.removeUpdates(listener)
        }
    }

    private fun android.location.Location.toGeoPoint(): GeoPoint =
        GeoPoint(
            lat = latitude,
            lon = longitude,
        )

    @SuppressLint("MissingPermission")
    private fun Context.hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val LOCATION_UPDATE_INTERVAL_MS = 2_000L
        const val LOCATION_UPDATE_MIN_DISTANCE_METERS = 5f
    }
}
