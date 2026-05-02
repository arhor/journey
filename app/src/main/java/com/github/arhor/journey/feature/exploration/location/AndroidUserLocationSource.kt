package com.github.arhor.journey.feature.exploration.location

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.UserLocationFix
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
                trySend(UserLocationUpdate.Available(location.toUserLocationFix()))
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

        val provider = locationManager.resolveLocationProvider()

        if (provider == null) {
            trySend(UserLocationUpdate.LocationServicesDisabled)
            return
        }

        locationManager.getLastKnownLocation(provider)
            ?.let {
                trySend(UserLocationUpdate.Available(it.toUserLocationFix()))
            } ?: trySend(UserLocationUpdate.TemporarilyUnavailable)

        locationManager.requestLocationUpdates(provider, cadence, listener)
    }

    private fun LocationManager.resolveLocationProvider(): String? = when {
        isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
        permissionChecker.hasFineLocationPermission() && isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
            LocationManager.GPS_PROVIDER
        }
        isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
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

    @SuppressLint("MissingPermission")
    private fun LocationManager.requestLocationUpdates(
        provider: String,
        cadence: ExplorationTrackingCadence,
        listener: LocationListener,
    ) {
        if (provider == LocationManager.FUSED_PROVIDER) {
            requestLocationUpdates(
                provider,
                LocationRequest.Builder(cadence.intervalMillis)
                    .setMinUpdateDistanceMeters(cadence.minDistanceMeters)
                    .setQuality(cadence.locationRequestQuality)
                    .build(),
                ContextCompat.getMainExecutor(context),
                listener,
            )
        } else {
            requestLocationUpdates(
                provider,
                cadence.intervalMillis,
                cadence.minDistanceMeters,
                listener,
                Looper.getMainLooper(),
            )
        }
    }

    private val ExplorationTrackingCadence.locationRequestQuality: Int
        get() = when {
            permissionChecker.hasFineLocationPermission() -> LocationRequest.QUALITY_HIGH_ACCURACY
            this == ExplorationTrackingCadence.FOREGROUND -> LocationRequest.QUALITY_BALANCED_POWER_ACCURACY
            else -> LocationRequest.QUALITY_LOW_POWER
        }

    private fun android.location.Location.toUserLocationFix(): UserLocationFix =
        UserLocationFix(
            location = GeoPoint(
                lat = latitude,
                lon = longitude,
            ),
            horizontalAccuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
            speedMetersPerSecond = if (hasSpeed()) speed.toDouble() else null,
            bearingDegrees = if (hasBearing()) bearing.toDouble() else null,
            bearingAccuracyDegrees = if (hasBearingAccuracy()) bearingAccuracyDegrees.toDouble() else null,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
        )

    private companion object {
        const val FOREGROUND_LOCATION_UPDATE_INTERVAL_MS = 2_000L
        const val FOREGROUND_LOCATION_UPDATE_MIN_DISTANCE_METERS = 1f
        const val BACKGROUND_LOCATION_UPDATE_INTERVAL_MS = 15_000L
        const val BACKGROUND_LOCATION_UPDATE_MIN_DISTANCE_METERS = 25f
    }
}
