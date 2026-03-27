package com.github.arhor.journey.feature.exploration.location

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidUserLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: LocationPermissionChecker,
) : UserLocationSource {

    override fun observeLocations(
        cadence: Flow<ExplorationTrackingCadence>,
    ): Flow<UserLocationUpdate> = cadence
        .distinctUntilChanged()
        .flatMapLatest(::observeLocationsForCadence)

    @SuppressLint("MissingPermission")
    private fun observeLocationsForCadence(
        cadence: ExplorationTrackingCadence,
    ): Flow<UserLocationUpdate> = callbackFlow {
        val locationManager = context.getSystemService<LocationManager>() ?: run {
            trySend(UserLocationUpdate.TemporarilyUnavailable)
            close()
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                trySend(UserLocationUpdate.Available(location.toGeoPoint()))
            }

            override fun onProviderDisabled(provider: String) {
                refreshSubscriptions(locationManager, cadence, this)
            }

            override fun onProviderEnabled(provider: String) {
                refreshSubscriptions(locationManager, cadence, this)
            }
        }

        val providersChangedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshSubscriptions(locationManager, cadence, listener)
            }
        }

        ContextCompat.registerReceiver(
            context,
            providersChangedReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        refreshSubscriptions(locationManager, cadence, listener)

        awaitClose {
            locationManager.removeUpdates(listener)
            context.unregisterReceiver(providersChangedReceiver)
        }
    }

    @SuppressLint("MissingPermission")
    private fun kotlinx.coroutines.channels.ProducerScope<UserLocationUpdate>.refreshSubscriptions(
        locationManager: LocationManager,
        cadence: ExplorationTrackingCadence,
        listener: LocationListener,
    ) {
        locationManager.removeUpdates(listener)

        if (!permissionChecker.hasAnyLocationPermission()) {
            trySend(UserLocationUpdate.PermissionDenied)
            return
        }

        if (!locationManager.isLocationEnabled) {
            trySend(UserLocationUpdate.LocationServicesDisabled)
            return
        }

        val providers = buildList {
            if (permissionChecker.hasFineLocationPermission() && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }

        if (providers.isEmpty()) {
            trySend(UserLocationUpdate.LocationServicesDisabled)
            return
        }

        providers.asSequence()
            .mapNotNull(locationManager::getLastKnownLocation)
            .firstOrNull()
            ?.let {
                trySend(UserLocationUpdate.Available(it.toGeoPoint()))
            } ?: trySend(UserLocationUpdate.TemporarilyUnavailable)

        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                cadence.intervalMillis,
                cadence.minDistanceMeters,
                listener,
                Looper.getMainLooper(),
            )
        }
    }

    private val ExplorationTrackingCadence.intervalMillis: Long
        get() = when (this) {
            ExplorationTrackingCadence.FOREGROUND -> FOREGROUND_LOCATION_UPDATE_INTERVAL_MS
            ExplorationTrackingCadence.BACKGROUND -> BACKGROUND_LOCATION_UPDATE_INTERVAL_MS
        }

    private val ExplorationTrackingCadence.minDistanceMeters: Float
        get() = when (this) {
            ExplorationTrackingCadence.FOREGROUND -> FOREGROUND_LOCATION_UPDATE_MIN_DISTANCE_METERS
            ExplorationTrackingCadence.BACKGROUND -> BACKGROUND_LOCATION_UPDATE_MIN_DISTANCE_METERS
        }

    private fun android.location.Location.toGeoPoint(): GeoPoint =
        GeoPoint(
            lat = latitude,
            lon = longitude,
        )

    private companion object {
        const val FOREGROUND_LOCATION_UPDATE_INTERVAL_MS = 2_000L
        const val FOREGROUND_LOCATION_UPDATE_MIN_DISTANCE_METERS = 5f
        const val BACKGROUND_LOCATION_UPDATE_INTERVAL_MS = 15_000L
        const val BACKGROUND_LOCATION_UPDATE_MIN_DISTANCE_METERS = 25f
    }
}
